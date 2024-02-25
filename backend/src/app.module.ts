import { MiddlewareConsumer, Module, NestModule } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { TypeOrmConfig } from './configs/typeorm.config';
import { UsersModule } from './user/user.module';
import { AuthModule } from './auth/auth.module';
import { WinstonModule } from 'nest-winston';
import { winstonConfig } from './configs/winston.config';
import { ProductModule } from './product/product.module';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { LoggerMiddleware } from './middlewares/logger.middleware';
import { MongooseModule } from '@nestjs/mongoose';
import { MONGODB_URL } from './constants';
import { ScheduleModule } from '@nestjs/schedule';
import { RedisModule } from '@songkeys/nestjs-redis';
import { RedisConfig } from './configs/redis.config';
import { MailerModule } from '@nestjs-modules/mailer';
import { MailerConfig } from './configs/mailer.config';

@Module({
    imports: [
        TypeOrmModule.forRoot(TypeOrmConfig),
        WinstonModule.forRoot(winstonConfig),
        UsersModule,
        AuthModule,
        ProductModule,
        MongooseModule.forRoot(MONGODB_URL),
        ScheduleModule.forRoot(),
        RedisModule.forRoot(RedisConfig),
        MailerModule.forRoot(MailerConfig),
    ],
    controllers: [AppController],
    providers: [AppService],
})
export class AppModule implements NestModule {
    configure(consumer: MiddlewareConsumer) {
        consumer.apply(LoggerMiddleware).exclude('/app').forRoutes('*');
    }
}
