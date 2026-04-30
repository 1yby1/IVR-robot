# FreeSWITCH 真实电话网关接入操作指南

这份文档用来说明：真实电话打进来后，系统如何接听、跑流程、播放语音、收按键、转接和挂断。

## 1. 先理解几个名词

- FreeSWITCH：电话交换机，负责真正接电话、放音、收按键、转接、挂机。
- ESL：FreeSWITCH 的控制接口。我们的 Java 后端通过 ESL 收事件、发命令。
- 热线：后台配置的被叫号码，例如 `4001`。电话打到这个号码后，系统用它找到要运行的 IVR 流程。
- 流程：后台流程编辑器画出来的节点图，例如欢迎语、按键分支、转人工、结束。
- callUuid：FreeSWITCH 给每通电话分配的唯一 ID。后台用它把事件、流程会话、通话记录串起来。

## 2. 当前已经实现的电话链路

1. 客户拨打热线号码。
2. FreeSWITCH 产生 `CHANNEL_CREATE` 事件。
3. 后端收到事件后，用被叫号码匹配 `ivr_hotline.hotline`。
4. 找到已启用热线后，加载已发布流程。
5. 后端创建 `call_log`，写入 `call_event`，并发送 `uuid_answer`。**流程会话置为等待 `answer` 但暂不播报**，避免给未应答的通道下发媒体命令。
6. FreeSWITCH 接通后产生 `CHANNEL_ANSWER` 事件。
7. 后端收到事件后从 start 节点开始推进流程。
8. `play` 节点发送播放命令（TTS 文本会先做参数消毒，避免 `|` / 换行破坏 `uuid_broadcast` 语义）。
9. `dtmf` 节点暂停流程，等待按键。
10. FreeSWITCH 收到按键后产生 `DTMF` 事件。
11. 后端把按键喂回流程执行器，流程按分支继续走。
12. `transfer` 节点对目标号码做白名单校验后发送转接命令；非法目标会被拒绝。
13. `end` 节点发送挂机命令。

录音 / ASR 链路：`asr` 节点触发 `uuid_record`；`RECORD_STOP` 事件回来后，后端先校验录音文件路径落在配置的 `recording-dir` 之内、且不超过 `max-recording-bytes`，再把"读文件 + 远程 ASR + 推进流程"丢到 `ivrAsrExecutor` 线程池跑，避免阻塞 ESL 事件线程。

## 3. 启动后端时启用真实网关

默认启动不会连接 FreeSWITCH。要接真实电话，需要启用 `freeswitch` profile：

```powershell
cd e:\QQ下载及记录\jianli\IVR\ivr-server

$env:FS_HOST="127.0.0.1"
$env:FS_PORT="8021"
$env:FS_PASSWORD="ClueCon"
$env:FS_RECORDING_DIR="/tmp/ivr/recordings"
$env:FS_DEFAULT_VOICE="zh|tts_commandline"        # 默认中文 TTS；纯英文 demo 可改 "en|kal"
$env:FS_MAX_RECORDING_BYTES="33554432"            # 单条录音最大 32 MB，防止恶意大文件 OOM

mvn -pl ivr-admin spring-boot:run -Dspring-boot.run.profiles=dev,freeswitch
```

如果 FreeSWITCH 在 Docker 里，`FS_HOST` 要写成 Java 后端能访问到的地址，不一定是 `127.0.0.1`。

## 4. FreeSWITCH 侧必须确认

1. `event_socket` 模块已启用。
2. ESL 监听端口一般是 `8021`。
3. ESL 密码和 `FS_PASSWORD` 一致。
4. 有一个拨号计划能把电话送到 FreeSWITCH，并产生被叫号码，例如 `4001`。
5. FreeSWITCH 能触发这些事件：
   - `CHANNEL_CREATE`
   - `CHANNEL_ANSWER`
   - `CHANNEL_HANGUP`
   - `DTMF`
   - `RECORD_STOP`

## 5. 后台配置检查

1. 流程管理里创建或使用一个流程。
2. 流程里至少包含：
   - `Start`
   - `Play`
   - `DTMF`
   - 一个按键分支，例如边的 key 是 `1`
   - `End` 或 `Transfer`
3. 发布流程。
4. 热线管理里新增热线：
   - 热线号码：例如 `4001`
   - 绑定流程：选择刚发布的流程
   - 状态：启用

## 6. 最小测试流程

建议先用最简单的流程测试：

```text
Start -> Play(欢迎语：欢迎致电，请按1) -> DTMF -> Play(你按了1) -> End
```

测试时观察后台：

1. 通话记录应出现一条新记录。
2. 通话事件里应看到 `inbound`、`enter`、`exit`、`wait`、`dtmf`、`terminate`。
3. 按 `1` 后流程应从 DTMF 节点继续到下一个节点。
4. 走到 End 后电话应挂机。

## 7. 常见问题

- 打进来没反应：先查 FreeSWITCH 是否能连 ESL，再查 `FS_HOST/FS_PORT/FS_PASSWORD`。
- 后台有 reject 事件：通常是被叫号码没有绑定启用热线，或者热线绑定的流程未发布。
- 按键不走分支：检查流程边的 `key` 是否就是你按的数字，例如 `1`。
- 电话不播放声音：先看后端日志里是否发出了播放命令，再检查 FreeSWITCH 的 TTS/音频播放能力。
- ASR 返回固定文本：当前还是占位 ASR，实现真实语音识别需要接阿里云 NLS、讯飞或其他 ASR 服务。
