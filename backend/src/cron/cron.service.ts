import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { ProductInfoDto } from 'src/dto/product.info.dto';
import { TrackingProductRepository } from '../product/trackingProduct.repository';
import { ProductRepository } from '../product/product.repository';
import { InjectModel } from '@nestjs/mongoose';
import { ProductPrice } from 'src/schema/product.schema';
import { Model } from 'mongoose';
import { CHANNEL_ID } from 'src/constants';
import { Cron } from '@nestjs/schedule';
import { FirebaseService } from '../firebase/firebase.service';
import { Message } from 'firebase-admin/lib/messaging/messaging-api';
import { TrackingProduct } from 'src/entities/trackingProduct.entity';
import Redis from 'ioredis';
import { InjectRedis } from '@songkeys/nestjs-redis';
import { getProductInfo } from 'src/utils/product.info';
import { CacheService } from 'src/cache/cache.service';

@Injectable()
export class CronService {
    constructor(
        @InjectRepository(TrackingProductRepository)
        private trackingProductRepository: TrackingProductRepository,
        @InjectRepository(ProductRepository)
        private productRepository: ProductRepository,
        @InjectModel(ProductPrice.name)
        private productPriceModel: Model<ProductPrice>,
        @InjectRedis() private readonly redis: Redis,
        private readonly firebaseService: FirebaseService,
        private cacheService: CacheService,
    ) {}

    private isDefined = <T>(x: T | undefined): x is T => x !== undefined;

    @Cron('0 */10 * * * *')
    async cyclicPriceChecker() {
        const totalProducts = await this.productRepository.find();
        const recentProductInfo = await Promise.all(
            totalProducts.map(async ({ productCode, id, shop }) => {
                const productInfo = await getProductInfo(shop, productCode);
                return { ...productInfo, productId: id };
            }),
        );
        const productList = recentProductInfo.map((data) => `product:${data.productId}`);
        const cacheData = await this.redis.mget(productList); // redis 접근 횟수 줄이기 위한 임시 방편
        const checkProducts = await Promise.all(
            cacheData.map((data, index) => this.getUpdatedProduct(recentProductInfo[index], data)),
        );
        const updatedProducts = checkProducts.filter(this.isDefined);
        if (updatedProducts.length > 0) {
            await this.productPriceModel.insertMany(
                updatedProducts.map(({ productId, productPrice, isSoldOut }) => {
                    return { productId, price: productPrice, isSoldOut };
                }),
            );
            const notifyingProducts = updatedProducts.filter((product) => !product.isSoldOut);
            if (notifyingProducts.length > 0) {
                await this.pushNotifications(notifyingProducts);
            }
        }

        totalProducts.sort((a, b) => a.id.localeCompare(b.id));
        recentProductInfo.sort((a, b) => a.productId.localeCompare(b.productId));
        const infoUpdatedProducts = totalProducts.filter((product, idx) => {
            const recentInfo = recentProductInfo[idx];
            if (product.productName !== recentInfo.productName || product.imageUrl !== recentInfo.imageUrl) {
                product.productName = recentInfo.productName;
                product.imageUrl = recentInfo.imageUrl;
                return true;
            }
        });
        if (infoUpdatedProducts.length > 0) {
            await this.productRepository.save(infoUpdatedProducts);
            await this.cacheService.updateByPriceChecker(infoUpdatedProducts);
        }
    }

    async getUpdatedProduct(data: ProductInfoDto, cacheData: string | null) {
        const { productId, productPrice, isSoldOut } = data;
        const cache = JSON.parse(cacheData as string);
        if (!cache || cache.isSoldOut !== isSoldOut || cache.price !== productPrice) {
            const lowestPrice = cache ? Math.min(cache.lowestPrice, productPrice) : productPrice;
            await this.redis.set(
                `product:${productId}`,
                JSON.stringify({
                    isSoldOut,
                    price: productPrice,
                    lowestPrice,
                }),
            );
            return data;
        }
    }

    async pushNotifications(notifyingProducts: ProductInfoDto[]) {
        const { messages, products } = await this.getNotifications(notifyingProducts);
        if (messages.length === 0) return;
        const { responses } = await this.firebaseService.getMessaging().sendEach(messages);
        const successProducts = products.filter((item, index) => {
            const { success } = responses[index];
            item.isFirst = false;
            return success;
        });
        if (successProducts.length > 0) {
            await this.trackingProductRepository.save(successProducts);
        }
    }
    async getNotifications(
        productInfo: ProductInfoDto[],
    ): Promise<{ messages: Message[]; products: TrackingProduct[] }> {
        const productIds = productInfo.map((p) => p.productId);

        const trackingProducts = await this.trackingProductRepository
            .createQueryBuilder('tracking_product')
            .where('tracking_product.productId IN (:...productIds)', { productIds })
            .getMany();

        const trackingMap = new Map<string, TrackingProduct[]>();
        trackingProducts.forEach((tracking) => {
            const products = trackingMap.get(tracking.productId) || [];
            trackingMap.set(tracking.productId, [...products, tracking]);
        });
        const results = await Promise.all(
            productInfo.map(async (product) => {
                const trackingList = product.productId ? trackingMap.get(product.productId) || [] : [];
                return await this.findMatchedProducts(trackingList, product);
            }),
        );

        const allNotifications = results.flatMap((result) => result.notifications);
        const allMatchedProducts = results.flatMap((result) => result.matchedProducts);

        return {
            messages: allNotifications,
            products: allMatchedProducts,
        };
    }

    async findMatchedProducts(trackingList: TrackingProduct[], product: ProductInfoDto) {
        const notifications = [];
        const matchedProducts = [];

        for (const trackingProduct of trackingList) {
            const { userId, targetPrice, isFirst, isAlert } = trackingProduct;
            if (!isFirst && targetPrice < product.productPrice) {
                trackingProduct.isFirst = true;
                await this.trackingProductRepository.save(trackingProduct);
            } else if (targetPrice >= product.productPrice && isFirst && isAlert) {
                const firebaseToken = await this.redis.get(`firebaseToken:${userId}`);
                if (firebaseToken) {
                    notifications.push(this.getMessage(product, firebaseToken));
                    matchedProducts.push(trackingProduct);
                }
            }
        }
        return { notifications, matchedProducts };
    }

    private getMessage(product: ProductInfoDto, token: string): Message {
        const { productPrice, productCode, productName, imageUrl, shop } = product;
        return {
            notification: {
                title: '목표 가격 이하로 내려갔습니다!',
                body: `${productName}의 현재 가격은 ${productPrice}원 입니다.`,
            },
            data: {
                shop,
                productCode,
            },
            android: {
                notification: {
                    channelId: CHANNEL_ID,
                    imageUrl,
                },
            },
            token,
        };
    }
}
