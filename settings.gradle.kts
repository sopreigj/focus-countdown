pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()

        // ==== 新增清华镜像 ====
        maven("https://maven.tuna.tsinghua.edu.cn/google/")
        maven("https://maven.tuna.tsinghua.edu.cn/maven-central/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // ==== 新增清华镜像 ====
        maven("https://maven.tuna.tsinghua.edu.cn/google/")
        maven("https://maven.tuna.tsinghua.edu.cn/maven-central/")
    }
}

rootProject.name = "Focus_countdown"
include(":app")
