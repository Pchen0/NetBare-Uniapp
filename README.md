# NetBare-Uniapp

一个用于 **Android 抓包与流量拦截** 的 UniApp 原生语言插件，基于 [NetBare-Android](https://github.com/MegatronKing/NetBare-Android) 封装。

> ⚠️ 注意：当前插件 **不支持 TLS 1.3**

------

## ✨ 功能特性

- 支持 HTTP / HTTPS 抓包（基于 VPN）
- 支持请求 / 响应 **拦截与修改**
- 支持 Header 修改
- 支持按 URL 前缀过滤
- 支持指定应用流量抓取
- 提供完整 UniApp JS 调用接口

------

## 📦 安装方式

### 1. 拷贝插件目录

从[Releases](https://github.com/Pchen0/NetBare-Uniapp/releases)下载打包好的插件，解压后放入Uniapp项目的nativeplugins文件夹：

```
你的UniApp项目/
└── nativeplugins/
    └── NetBare-Uniapp/
        └── android/
            ├── package.json
            ├── netbare-core.aar
            ├── netbare-injector.aar
            └── NetBare-Uniapp.aar
```

------

### 2. 启用插件

在 `manifest.json` → **App 原生插件配置** 中选择本地插件：

```
NetBare-Uniapp
```

------

### 3. 使用自定义基座

> ❗ 必须使用 **自定义基座运行**
>
> 标准基座不支持本地原生插件

------

## 🚀 快速开始

### 1. 引入插件

```js
const netbare = uni.requireNativePlugin('NetBare-Uniapp')
```

------

### 2. 绑定 Application（必须）

建议在 `App.vue -> onLaunch` 中执行：

```js
netbare.attachApplication(true, (res) => {
  console.log('attachApplication', res)
})
```

------

### 3. 设置拦截规则（可选）

```js
netbare.setInterceptUrls(
  '["https://api.", "http://example.com"]',
  () => {}
)
```

- 不设置或传 `[]` → 拦截所有流量

------

### 4. 设置拦截回调

```js
netbare.setInterceptHandler((data) => {
  const { type, callbackId, url, method, statusCode, body, headersJson } = data

  switch (type) {

    // ===== 请求体 =====
    case 'request':
      netbare.setRequestModification(callbackId, body, () => {})  // 不修改则直接返回body
      break

    // ===== 响应体 =====
    case 'response': {
      let resBody = JSON.stringify({
        code: 0,
        msg: '修改后的body内容'
      })

      // chunked rebuild（必须）
      const hexLen = byteLength(resBody).toString(16)
      const rebuilt =
        hexLen + '\r\n' +
        resBody + '\r\n' +
        '0\r\n\r\n'

      const base64 = btoa(rebuilt)

      netbare.setResponseModification(callbackId, base64, () => {}) // 返回修改后的body(base64)
      break
    }

    // ===== 请求头 =====
    case 'requestHeader': {
      netbare.setRequestHeaderModification(callbackId, headersJson, () => {})

      const headers = headersJson ? JSON.parse(headersJson) : []
      const ua = headers.find(h => h.name === 'User-Agent')?.value

      console.log('UA:', ua)
      break
    }

    // ===== 响应头 =====
    case 'responseHeader':
      netbare.setResponseHeaderModification(callbackId, headersJson, () => {})
      break
  }
})
```

注意修改body后必须经过chunked rebuild，其中byteLength为：

```js
function byteLength(str) {
	let len = 0

	for (let i = 0; i < str.length; i++) {
		const code = str.charCodeAt(i)

		if (code <= 0x7f) {
			len += 1
		} else if (code <= 0x7ff) {
			len += 2
		} else if (code <= 0xffff) {
			len += 3
		} else {
			len += 4
			i++
		}
	}

	return len
}
```



------

### 5. 申请 VPN 权限

```js
netbare.prepare((res) => {
  if (res.needPermission) {
    if (res.intentStarted) {
      // 已弹出授权页，用户授权后回到应用可再次调用 prepare() 或直接 start()
    } else {
      // 未拉起授权页时可提示用户前往系统设置开启 VPN 权限
    }
  } else {
    // 已授权，可调用 start()
  }
})
```

------

### 6. 启动抓包

```js
const config = {
  mtu: 4096,                          // 可选 MTU
  address: '10.1.10.1',            // 可选，虚拟 IP (ip/prefix)
  routes: ['0.0.0.0/0'],              // 可选路由
  dnsServers: ['114.114.114.114', '114.114.115.115'], // 可选 DNS
  allowedApplications: ['com.your.app'],  // 允许走 VPN 的应用包名列表（可选）
  disallowedApplications: [],             // 禁止走 VPN 的应用（可选）
  allowedHosts: [],                       // 只抓这些 host（可选）
  disallowedHosts: [],                    // 不抓这些 host（可选）
  dumpUid: false,                         // 是否 dumpUid（会耗电，Android Q 以上有限制）
  excludeSelf: true                       // 是否排除自身应用的流量（依赖 dumpUid）
}

netbare.start(
  'YourJksAlias',
  'YourJksCn',
  JSON.stringify(config),
  (res) => {
    if (res === true) console.log('start ok')
    else console.log('start error', res)
  }
)
```

------

### 7. 停止 / 状态查询

```js
netbare.stop(() => {})

netbare.isActive((res) => {
  console.log('active', res.active)
})
```

------

## 📘 API 说明

### 拦截相关

#### `setInterceptUrls(urlPatternsJson)`

- 参数：JSON 字符串数组
- 示例：

```
'["https://api.example.com/"]'
```

------

#### `setInterceptHandler(callback)`

拦截回调，根据 `type` 区分阶段：

| type           | 说明   |
| -------------- | ------ |
| request        | 请求体 |
| response       | 响应体 |
| requestHeader  | 请求头 |
| responseHeader | 响应头 |

------

### 数据格式说明

#### body

- Base64 编码
- 使用：

```
const text = atob(body)
```

------

#### headersJson

格式：

```
[
  { "name": "User-Agent", "value": "xxx" },
  { "name": "Cookie", "value": "a=1" }
]
```

------

## ✏️ 修改数据说明

### 请求 / 响应修改接口

```
setRequestModification(callbackId, base64, callback)
setResponseModification(callbackId, base64, callback)
setRequestHeaderModification(callbackId, headersJson, callback)
setResponseHeaderModification(callbackId, headersJson, callback)
```

------

### ⚠️ 注意事项

- 修改必须在 **10 秒内完成**
- `body` 必须是 **Base64**
- HTTP 响应修改需要 **chunked rebuild**

------

## 🔐 VPN 授权机制

- 基于 Android `VpnService`
- `prepare()` 会尝试拉起授权弹窗
- 用户授权后需重新调用 `prepare()` 或直接 `start()`
