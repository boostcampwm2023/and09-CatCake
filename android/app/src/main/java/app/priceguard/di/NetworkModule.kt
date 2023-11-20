package app.priceguard.di

import app.priceguard.data.network.UserAPI
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.priceguard.app"
    private val json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideUserAPI(): UserAPI = Retrofit.Builder()
        .baseUrl("${BASE_URL}/user/")
        .addConverterFactory(json.asConverterFactory(MediaType.parse("application/json")!!))
        .build()
        .create(UserAPI::class.java)
}
