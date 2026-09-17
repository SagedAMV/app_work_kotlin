// وحدة التطبيق — «مجرة» إدارة المواقع الشخصية (النسخة المبسطة 2.0)
// أوفلاين 100%: لا يوجد أي إذن إنترنت في AndroidManifest ولا أي مكتبة تتطلب اتصالًا.
//
// ما حُذف في 2.0 حسب تعليمات التبسيط:
// - WorkManager و hilt-work (لا جدولة دورية ولا عمال تنبيهات)
// - androidx.biometric (القفل بسيط برمز سري، بلا بصمة)
// - androidx.fragment (كانت مطلوبة لـ BiometricPrompt فقط)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.majarra.galaxy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.majarra.galaxy"
        minSdk = 30          // حسب التعليمات: Android 11+
        targetSdk = 34
        versionCode = 6
        versionName = "2.3.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    // اختبارات الوحدة: تفشل بدل أن تُرجع قيمًا وهمية صامتة
    testOptions {
        unitTests.isReturnDefaultValues = false
    }

    lint {
        abortOnError = true
    }
}

dependencies {
    // الأساسيات
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    // مراقبة الـ Flow بأمان من دورة الحياة (بدل collectAsState غير المرتبط بالدورة)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Jetpack Compose (Material 3)
    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // التنقل
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt — حقن الاعتماديات
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room — قاعدة البيانات المحلية
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // الصور المحلية فقط (مرفقات) — لا يحتاج إنترنت
    implementation("io.coil-kt:coil-compose:2.5.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // اختبارات الوحدة (التحقق من المدخلات)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
