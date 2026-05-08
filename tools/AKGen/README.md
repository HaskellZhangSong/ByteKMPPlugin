# ArkTS 到 Kotlin FFI 转换工具

一个用于将 ArkTS 声明转换为 Kotlin 包装器的命令行工具，专为 HarmonyOS 开发设计。

## 功能特性

- ✅ 将 ArkTS 接口声明自动转换为 Kotlin FFI 包装器代码
- ✅ 支持复杂的类型映射和转换规则
- ✅ 自动生成必要的导入和注解
- ✅ 配置驱动的转换流程
- ✅ 跨平台支持（macOS/Linux/Windows）

## 环境要求

### 开发环境

- Python >= 3.13
- [Poetry](https://python-poetry.org/) >= 1.5.0
- macOS（推荐）或 Linux/Windows

#### 安装 pipx and poetry

Generic/Pip: 一般使pip安装就好
```
python3 -m pip install --user pipx
```

macOS
```
brew install pipx
```

Linux (Debian/Ubuntu): 
```
sudo apt update 
sudo apt install pipx
```

Windows: 
```
python -m pip install --user pipx
```

#### 安装 poetry
```
pipx install poetry
```

### 运行环境

- 无需 Python 环境（使用打包后的二进制文件）
- macOS 10.13+ (Intel/Apple Silicon)
- 兼容 Linux 和 Windows（需相应平台的打包版本）

### 依赖项

项目核心依赖：
- `tree-sitter` >= 0.25.2：语法解析引擎
- `tree-sitter-kotlin` >= 1.1.0：Kotlin 语法解析器
- `tree-sitter-typescript` >= 0.23.2：TypeScript 语法解析器
- `typing-extensions` >= 4.12.2：类型提示扩展

## 编译/打包指南

### 1. 准备开发环境

```bash
# 克隆项目
git clone <repository-url>
cd python-arkts-ffi-kt

# 安装 Poetry（如果尚未安装）
curl -sSL https://install.python-poetry.org | python3 -

# 安装项目依赖
poetry install

# 激活虚拟环境
poetry shell
```

### 2. 安装打包工具

```bash
# 在项目虚拟环境中安装 PyInstaller
pip install pyinstaller
```

### 3. 打包为独立二进制文件

#### macOS 打包（推荐配置）

```bash
# 清理之前的构建缓存
pyinstaller --clean arkts-ffi-converter.spec
```

### 4. 验证打包结果

```bash
# 检查生成的二进制文件
ls -la dist/arkts-ffi-converter

# 添加执行权限（macOS/Linux）
chmod +x dist/arkts-ffi-converter

# 测试运行
./dist/arkts-ffi-converter --help
```

### 5. 分发说明

打包后的二进制文件 `dist/arkts-ffi-converter` 是完全独立的，可以在没有 Python 环境的系统上运行。

## 使用指南

### 基本用法

```bash
./arkts-ffi-converter \
  --config /path/to/config.json
```

### 命令行参数

| 参数 | 必需 | 类型 | 描述 |
|------|------|------|------|
| `--config` | ✅ | 路径 | 配置文件路径 |

### 配置文件格式

配置文件采用 JSON 格式，定义转换规则和输出设置：

```json
{
  "kmp.dir": "/path/to/kmp/project",
  "ohos.dir": "/path/to/ohos/project",
  "ios.dir": "/path/to/ios/project",
  "proxy": {
    "example_proxy": {
      "shareType": "proxy",
      "enableShareId": true,
      "enableIosEmptyImpl": true,
      "enableJsonName": true,
      "suffix": "Proxy",
      "defaultPackage": "com.example.project",
      "android": {
        "srcDir": ["./src/androidMain/kotlin"],
        "kotlinOut": "./src/androidMain/kotlin/generated"
      },
      "common": {
        "kotlinOut": "./src/commonMain/kotlin/generated",
        "entityDir": {
          "commonMain": ["./src/commonMain/kotlin/entities"],
          "ohosArm64Main": ["./src/ohosArm64Main/kotlin/entities"]
        }
      },
      "ohos": {
        "common": {
          "kotlinOut": "./src/ohosArm64Main/kotlin/generated",
          "import": [
            "com.example.ohos_ffi.types.ArkObjectSafeReference",
            "platform.ohos.napi.*",
            "kotlinx.cinterop.*"
          ]
        },
        "packages": {
          "@example/dto": {
            "arktsSrcDir": ["/path/to/arkts/source"]
          }
        }
      },
      "ios": {
        "common": {
          "kotlinOut": "./src/iosMain/kotlin/generated"
        }
      }
    }
  }
}
```

#### 配置项说明

##### 顶层配置项
- `kmp.dir`：KMP 项目根目录路径（必需）
- `ohos.dir`：Ohos 项目根目录路径（必需）
- `ios.dir`：iOS 项目根目录路径（可选）
- `proxy`：代理配置对象，包含多个代理配置块

##### 代理配置项（proxy.*）
- `shareType`：共享类型，必须是 "proxy"、"copy" 或 "basic"（必需）
- `enableShareId`：是否启用共享 ID（可选，默认 false）
- `enableIosEmptyImpl`：是否为 iOS 启用空实现（可选，默认 false）
- `enableJsonName`：是否启用 JSON 名称（可选，默认 false）
- `suffix`：生成类的后缀（可选，默认 "Proxy"）
- `defaultPackage`：默认包名（可选，默认 ""）

##### 平台配置
- `android.srcDir`：Android 源码目录列表（仅当 shareType 为 "proxy" 时有效）
- `android.kotlinOut`：Android 平台 Kotlin 输出路径（仅当 shareType 为 "proxy" 时有效）
- `common.kotlinOut`：通用平台 Kotlin 输出路径
- `common.entityDir`：实体目录配置（仅当 shareType 为 "copy" 时有效）
- `ohos.common.kotlinOut`：Ohos 平台 Kotlin 输出路径（必需）
- `ohos.common.import`：Ohos 平台需要导入的包列表（可选）
- `ohos.packages`：Ohos 包映射配置
- `ios.common.kotlinOut`：iOS 平台 Kotlin 输出路径（可选）

## 开发指南

### 单元测试

#### kotlin parser
```
poetry run python -m unittest ./test/test_kt_parser.py
```

#### custom_* 代码的缩进测试 

```
poetry run python -m unittest ./test/convert/pretty.py    
```