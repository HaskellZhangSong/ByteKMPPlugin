# ByteKMP 容器插件

## 目录结构
```text
├── README.md
├── build.gradle.kts
├── gradle-plugin # 存放编译插件
│   ├── har_bundler
│   └── publish
├── processor # 存放 ksp 插件
│   ├── ffi_processor
│   └── spi_processor
├── runtime # 存放 所需的运行库
│   ├── ffi_runtime
│   └── spi_runtime
├── gradle
│   └── libs.versions.toml # 管理版本号
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts
```
目录说明：
- gradle-plugin：存放所有编译插件
- processor：存放所有 ksp 插件
- runtime: 存放所有运行库
- libs.version.toml：负责管理所有版本号

## 组件发布
### 配置私有 maven
```shell
export custom_maven_url=私有Maven仓库地址
export custom_maven_publish_url=私有Maven仓库publish地址
export custom_maven_publish_username=**user**
export custom_maven_publish_password=**password**
```

```shell
# 发布到 Maven
./gradlew -PARTIFACT_VERSION=2.0.0 publishAll
# 发布到本地
./gradlew publishAllToMavenLocal
```
