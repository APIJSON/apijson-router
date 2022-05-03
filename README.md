# apijson-router  [![](https://jitpack.io/v/APIJSON/apijson-router.svg)](https://jitpack.io/#APIJSON/apijson-router)
腾讯 [APIJSON](https://github.com/Tencent/APIJSON) 5.0.5+ 的路由插件，对外暴露类 RESTful 简单接口，内部转成 APIJSON 格式请求来执行。<br />
A router plugin for Tencent [APIJSON](https://github.com/Tencent/APIJSON) 5.0.5+, expose RESTful-like HTTP API, transfer to APIJSON request and execute.

![image](https://user-images.githubusercontent.com/5738175/166560119-c598d3c6-48b6-4f47-85fe-8f36ca332e99.png)

## 添加依赖
## Add Dependency

### Maven
#### 1. 在 pom.xml 中添加 JitPack 仓库
#### 1. Add the JitPack repository to pom.xml
```xml
	<repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
	</repositories>
```
<br />

#### 2. 在 pom.xml 中添加 apijson-router 依赖
#### 2. Add the apijson-router dependency to pom.xml
```xml
	<dependency>
	    <groupId>com.github.APIJSON</groupId>
	    <artifactId>apijson-router</artifactId>
	    <version>LATEST</version>
	</dependency>
```

<br />
<br />

### Gradle
#### 1. 在项目根目录 build.gradle 中最后添加 JitPack 仓库
#### 1. Add the JitPack repository in your root build.gradle at the end of repositories
```gradle
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
<br />

#### 2. 在项目某个 module 目录(例如 `app`) build.gradle 中添加 apijson-router 依赖
#### 2. Add the apijson-router dependency in one of your modules(such as `app`)
```gradle
	dependencies {
	        implementation 'com.github.APIJSON:apijson-router:latest'
	}
```

<br />
<br />

## 初始化
## Initialization

#### 1.新增一个 @RestController class DemoController extends APIJSONRouterController
#### 1.Add a @RestController class DemoController extends APIJSONRouterController

<br />

#### 2.在 DemoController 重写 router 方法，加上注解 @PostMapping("router/{method}/{tag}")
#### 2.Override router in DemoController, and add @PostMapping("router/{method}/{tag}") for router method

<br />

#### 3.在 DemoApplication.main 方法内，APIJSONAppication.init 后调用 APIJSONRouterApplication.init
#### 3.In DemoApplication.main, call APIJSONRouterApplication.init after APIJSONAppication.init

<br />


参考 [APIJSONRouterController](/src/main/java/apijson/router/APIJSONRouterController.java) 的注释及 [APIJSONBoot](https://github.com/APIJSON/APIJSON-Demo/blob/master/APIJSON-Java-Server/APIJSONBoot) 的 [DemoController](https://github.com/APIJSON/APIJSON-Demo/blob/master/APIJSON-Java-Server/APIJSONBoot/src/main/java/apijson/boot/DemoController.java) 和 [DemoApplication](https://github.com/APIJSON/APIJSON-Demo/blob/master/APIJSON-Java-Server/APIJSONBoot/src/main/java/apijson/boot/DemoApplication.java) <br />

See document in [APIJSONRouterController](/src/main/java/apijson/router/APIJSONRouterController.java) and [DemoController](https://github.com/APIJSON/APIJSON-Demo/blob/master/APIJSON-Java-Server/APIJSONBoot/src/main/java/apijson/boot/DemoController.java), [DemoApplication](https://github.com/APIJSON/APIJSON-Demo/blob/master/APIJSON-Java-Server/APIJSONBoot/src/main/java/apijson/boot/DemoApplication.java)  in [APIJSONBoot](https://github.com/APIJSON/APIJSON-Demo/blob/master/APIJSON-Java-Server/APIJSONBoot)

<br />
<br />

## 使用
## Usage
### 1.配置 Document 请求映射

例如 <br />

name: 查询动态列表

url: /router/get/momentList  // 最后两个 key 必须以 APIJSON 的万能通用 API 之一的路由开头，例如 /get/, /post/ 等

request:
```js
{
    "Moment[].page": 0,  // 以 . 分割路径中的 key，映射以下  "Moment[]": { "page": 0 }
    "Moment[].count": 10,  // 以 . 分割路径中的 key，映射以下  "Moment[]": { "count": 10 }
    "format": false  // 映射以下 "format": false
}
```

apijson:
```js
{
    "Moment[]": {
        "page": 0,
        "count": 10,
        "Moment": {
            "@column": "id,userId,date"
        }
    },
    "format": false
}
```

其它字段可不填，用默认值

![image](https://user-images.githubusercontent.com/5738175/166565083-1db03cde-8b59-4048-af6d-78d9efb78f7c.png)

<br />

### 2.配置 Request 表校验规则
如果不需要校验参数则可跳过。 <br />

和普通的 APIJSON 格式请求基本一致，只是不会自动根据符合表名的 tag 来对 structure 包装一层 "Table": structure

例如 <br />

method: GET

tag: moments

structure:
```js
{
    "MUST": "Moment[].page",  // 必传 Moment[].page
    "REFUSE": "!Moment[].count,!format,!",  // 不禁传 Moment[].count 和 format，禁传 MUST 之外的其它所有 key
    "TYPE": {
        "format": "BOOLEAN",  // format 类型必须是布尔 Boolean
        "Moment[].page": "NUMBER",  // Moment[].page 类型必须是整数 Integer
        "Moment[].count": "NUMBER"  // Moment[].count 类型必须是整数 Integer
    }
}
```

![image](https://user-images.githubusercontent.com/5738175/166563592-e8d3f09f-471a-4ae1-bee9-de78ec16fefe.png)

<br />

#### 步骤 1, 2 都可在 APIAuto 参数注入面板点击 \[+ 添加] 按钮，再点击弹窗内 \[发布简单接口] 按钮来自动完成

![image](https://user-images.githubusercontent.com/5738175/166562199-4d96dd16-cf25-4bd4-b574-94a3c5f32685.png)

<br />

### 3.测试类 RESTful API

启动项目后用 APIAuto/Postman 等 HTTP 接口测试工具发起请求 <br />

POST {base_url}/router/get/{tag}  // tag 可为任意符合变量名格式的字符串
```js
{
    "showKey0": val0,
    "showKey1.a1": val1,
    "showKey2.a2.b2": val2
    ...
}
```

例如 <br />

POST http://localhost:8080/router/get/momentList  // 对应 Document 表配置的 url
```js
{
    "Moment[].page": 1,
    "Moment[].count": 3,
    "format": true
}
```

如果 parser.isNeedVerifyContent，则会经过 Request 表校验规则来校验， <br />


最后内部映射为： <br />
```js
{
    "Moment[]": {
        "page": 1,
        "count": 3,
        "Moment": {
            "@order": "date-"
        }
    },
    "format": true
}
```

执行完 APIJSON 格式的请求后返回对应结果

![image](https://user-images.githubusercontent.com/5738175/166560119-c598d3c6-48b6-4f47-85fe-8f36ca332e99.png)


<br /><br />

#### 对你有用的话点右上角 ⭐Star 支持一下，谢谢 ^_^
#### Please ⭐Star this project ^_^
https://github.com/APIJSON/apijson-router
