<p align="center"><img src="frontend/public/brand/logo.jpg" alt="知华科技 ZhuaTech" width="180"></p>

# BoxTime · 知华集装箱免箱期与超期费用对账

**知华科技（上海如静知华信息科技有限公司）** · [官网](https://www.zhuatech.cn/) · 商业咨询微信 **zhuatech / zhuatech2**。

**公开源码学习版／非商业源码版。未经书面授权不得商用。** 自有代码适用 [LICENSE](LICENSE)；第三方依赖保持各自许可，见 [第三方声明](THIRD_PARTY_NOTICES.md)。本项目不承诺船公司官方费率，不替代运输合同、正式账单或财务系统。

## 业务与适用场景

进口集装箱的卸船、提箱、还空箱日期决定免箱期和超期费用。货主、货代、箱务运营、复核人员与财务可在同一案件中核对约定、延期证据、逐日费用和承运账单。每个案件处理一个箱；同一提单的多个箱分别建案。

业务依据可参考 [Maersk D&D 条款](https://terms.maersk.com/dnd)：码头内（Demurrage）、码头外（Detention）及合并计费有不同适用期间。具体合同的起止日、日历和费率须由负责人员核实后配置。这里没有导入或假定任何船公司的实际报价。

## 已实现功能

| 模块 | 可操作功能 |
|---|---|
| 客户档案 | 新增、编辑、启停、未引用删除；部门归属保护 |
| 计费约定 | 草稿编辑、独立批准、草稿作废、有效日期、港口时区、箱型；批准后不可改价 |
| 规则配置 | 分开/合并计费，日历日/周一至周五，排除日期，两端包含规则，分阶段免费天数，两段日费率 |
| 箱务 | 草稿编辑、启用、登记提箱、登记还空箱、作废；合同快照、箱号格式、提还箱日期顺序检查 |
| 延期审批 | 分阶段延期申请、不同人员批准/驳回，凭据记录；待审批时阻止最终核算 |
| 费用核算 | 在用箱按日期预估，已还箱生成最终逐日明细；另一位授权人员复核 |
| 承运账单 | 实际金额和凭据编号、核算差额、客户确认/争议、争议修订与再次确认 |
| 付款 | 分笔记录已完成的线下付款，禁止超付与重复凭据；另一位人员冲正，恢复余额 |
| 查询与统计 | 编号/箱号/提单搜索、状态筛选、分页、编号/最新排序、费用和箱务状态统计 |
| 证据导出 | 合同和箱务 JSON 下载；包含快照、日费明细、延期、款项和不可编辑操作轨迹 |
| 系统管理 | 账号、密码、角色、已注册接口权限、菜单、部门、箱型字典、系统参数、审计 |
| 用户页面 | 中文/English切换，电脑/手机布局，岗位导航，绑定货主的客户操作与数据隔离 |

空库只初始化管理员、岗位、权限、菜单、箱型字典与系统参数。**不生成业务案例或客户数据。** 验收脚本产生明确标注 TEST 的合成记录，截图展示隔离测试库中的这些记录。

## 核心规则与边界

1. 进口码头内期间：卸船 → 提箱；码头外：提箱 → 还空箱；合并：卸船 → 还空箱。开始日与结束日是否计入均由约定指定；默认计开始、不计结束，分开模式的提箱日因此只计入码头外阶段。
2. 免费与付费日使用同一计数日历；排除日期在日历日和工作日模式均跳过。WORKDAY只表示周一至周五，**不自动理解法定调休**。补班、只排除免费期、不连续日费率等条款未实现，须核对适用性。
3. 第一个付费日起累计分段：前 `tierDays` 个付费日采用第一费率，其后采用后续费率。免费截止日是合同计算值；有延期则在原免费天数上增加已批准天数。
4. 同一案件的一个阶段至多保留一条待批或已批延期；已驳回可重新申请。免费天数加批准延期最多730，单次延期最多365；排除日期最多200个，期间最多5年。金额仅CNY、两位小数，单个输入不超过一亿元。
5. 箱事件按港口本地日人工录入，不转换航迹时间戳。日期须有顺序且不晚于该港口今天。箱号只验证4字母7数字格式，未验证ISO校验位。
6. 已批准的约定与案件合同快照不随档案编辑变化。已提交的箱动态不可直接修改；核算前发现错误可作废案件、带新编号重建，原证据保留。已还箱案件不支持撤销或回滚，需要确认事实后再登记。
7. 同一约定、提单与箱号最多有一个非作废案件；案件编号、约定编号和付款凭据编号全库唯一。账单差额须在录入说明中解释并由客户重新确认，付款记录不发起真实转账。
8. 双人规则按**账号身份**执行；同人多账号无法自动识别，账号分配与客户确认凭据由管理人员负责。内部人员有确认权限时可登记客户已完成的线下确认，必须保留对应凭据说明。
9. 业务命令带版本与UUID；旧版本拒绝，原UUID相同载荷重试不会再次执行，返回当前记录。UUID绑定操作账号、对象、动作与完整载荷。更换UUID不是撤销已执行动作。
10. 全局管理锁串行化写事务，优先保证学习版审批、角色变更与付款一致性。合同与案件各受 `maxShipments` 限制（默认1000，100–1000），目录读取上限10000；适合小团队学习与隔离验证。

## 角色、权限与数据范围

| 初始角色 | 职责 |
|---|---|
| 管理员 | 全部管理与业务权限；仍受不同人复核和客户绑定限制 |
| 箱务运营 | 编制约定/案件、箱动态、延期申请、费用核算、账单编制 |
| 独立复核 | 批准约定、批准/驳回延期、复核费用 |
| 财务 | 客户档案、承运账单、线下付款及独立冲正 |
| 货主客户 | 查看所属客户案件、延期申请、确认/争议、统计与导出 |

权限校验在接口执行，页面按钮不是安全边界。ALL=全部部门；DEPARTMENT=本部门；SELF=本人创建的案件/约定。**绑定货主优先于角色范围**：只读同部门该货主案件，不能使用内部操作、后台管理或内部审计；即使误配ALL管理员角色也受此限制。客户角色必须绑定客户；未绑定则按普通内部SELF范围解释，不应发给外部用户。角色/启停变化实时生效，密码改变使旧会话失效。最后一个启用、未绑定客户的ALL管理员受保护。

## 实际运行截图

下列截图来自当前运行页面，TEST记录仅用于隔离验收。

### 登录与首页

![登录](docs/screenshots/login.jpg)

![箱务首页](docs/screenshots/workbench.jpg)

### 核心业务与客户操作

![箱务与逐日费用](docs/screenshots/container.jpg)

![货主账单确认](docs/screenshots/customer.jpg)

### 后台管理、统计与权限

![账号管理](docs/screenshots/accounts.jpg)

![费用统计](docs/screenshots/statistics.jpg)

![角色权限](docs/screenshots/roles.jpg)

![手机页面](docs/screenshots/mobile.jpg)

## 架构与版本

| 层 | 版本 / 职责 |
|---|---|
| 后端 | Java21、Maven3.9、SpringBoot4.0.7、SpringSecurity、JPA、Flyway |
| 数据库 | MySQL8.4、MariaDB JDBC3.5.10兼容驱动、版本化SQL |
| 前端 | Vue3.5.40、Vite8.1.5、Node24.19.0、npm11、Lucide1.48 |
| 部署 | DockerCompose v2、Nginx1.29，非root应用，数据库卷持久化 |

浏览器 → Nginx → SpringBoot → MySQL。会话cookie与CSRF，金额BigDecimal，存证微秒UTC时间、计费LocalDate。本项目复用知华公共身份与部署基础代码，箱务领域和页面独立实现。详情见 [架构说明](docs/架构说明.md)。

```text
backend/src/main/java/cn/zhuatech/boxtime/  业务、身份与接口
backend/src/main/resources/db/migration/  V1身份、V2箱务
backend/src/test/                        计费与HTTP/JPA测试
frontend/src/                           Vue操作页面与前端测试
frontend/public/brand/                  正式LOGO和两张原二维码
scripts/                                初始化、真实验收、发布检查
compose.yaml                            MySQL、后端与前端
```

## 本地快速运行

安装 Docker Desktop / Docker Engine 与 Compose v2、Python3.10+。首次拉取镜像和依赖需要网络。使用终端进入项目目录：

```bash
python3 scripts/init-env.py
docker compose up -d --build
docker compose ps
```

初始化脚本独立生成数据库、root和管理员随机密码，写入权限0600且已忽略的 `.env`，不覆盖已有文件。账号是 **admin**；管理员初始密码在本地 `.env` 的 `ADMIN_PASSWORD`，不是固定口令。请本地读取并保密。在账号管理中创建不同人员的运营与复核账号后再配置业务；同一个管理员不能批准自己编制的约定。

- 浏览器：[http://127.0.0.1:8126/](http://127.0.0.1:8126/)
- 健康检查：[http://127.0.0.1:8126/actuator/health](http://127.0.0.1:8126/actuator/health)
- 默认只监听本机；数据库与后端未直接发布宿主机端口。

`.env.example` 列出环境变量名称。`DATABASE_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`ADMIN_PASSWORD` 必填；`WEB_PORT` 默认8126、`BIND_ADDRESS` 默认127.0.0.1、`COOKIE_SECURE` 本地false。HTTPS公网部署须显式配置安全cookie、TLS和受控访问，不能直接沿用本机配置。

## 业务启动步骤

创建客户 → 编制约定 → 另一位复核员批准 → 编制箱务 → 启用 → 提箱/还空箱 → 处理延期 → 核算 → 另一位复核员复核 → 录入承运账单 → 货主确认或争议 → 财务记录付款。详细字段与争议处理见 [操作手册](docs/操作手册.md)，接口见 [接口说明](docs/接口说明.md)。

## 数据库与升级

数据库名 `zhuatech_boxtime`，账号 `boxtime`。脚本 [V1身份结构](backend/src/main/resources/db/migration/V1__identity.sql) 与 [V2箱务结构](backend/src/main/resources/db/migration/V2__container_time.sql) 由Flyway在空库自动执行，Hibernate只校验结构。包括账号、角色、权限、菜单、部门、字典、参数、审计、客户、约定、箱务、延期、付款、命令重试与业务事件；不含业务演示初始化。

先备份，再以新版本镜像启动；保留MySQL卷，新增迁移升级，**不得改写已执行迁移**。管理员初始化密码只在空库使用；调整环境变量不会重置已有密码。停止服务用 `docker compose down`，不加 `-v`；删除卷会永久删除数据。备份、恢复与升级步骤见 [部署手册](docs/部署手册.md)。

## 测试与构建

后端测试使用隔离H2兼容模式；真实MySQL验收与其分开进行。Dockerfile也执行完整后端和前端测试，不跳过测试。

```bash
mvn -B -f backend/pom.xml spotless:check test package
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose config --quiet
git diff --check
```

改动格式时使用 `mvn -f backend/pom.xml spotless:apply` 与 `npm --prefix frontend run format`。真实业务验收脚本只运行在新建的、可清理的隔离库：

```bash
docker compose -p boxtime-check up -d --build
python3 scripts/smoke.py --allow-test-writes
python3 scripts/smoke.py --verify
python3 scripts/release-check.py
```

同一项目不能同时用相同端口启动两个实例；恢复验证可通过 `WEB_PORT=18126` 与 `TEST_URL=http://127.0.0.1:18126` 使用独立Compose项目。脚本生成私有 `output/qa-state.json` 用于重启/恢复比对，包含合成测试账号口令，已被Git忽略，不得分享。测试覆盖完整流程、日期与费率边界、独立复核、客户/部门隔离、CSRF、旧版本、重试、竞争付款与引用保护。执行结果应以实际日志为准。

## 安全与未实现事项

没有承运商API、GPS/AIS、收费短信/邮件、支付网关、发票系统或银行调用，不需要外部API配置。未实现出口集装箱、仓储费、法定调休日历、任意多段费率、多币种/汇率、附件上传、合同OCR、定时催办、撤回已还箱事件或财务分录。凭据为人工录入编号与说明，不是附件真实性验证。

TLS、外部域名、备份保管和正式客户数据导入需部署人员配置。源码学习版的全局写锁、目录上限和单币种边界不适合作为未经评估的大规模生产系统。安全边界与公开源码授权见 [安全说明](docs/安全说明.md)。

## 联系知华科技

**知华科技（上海如静知华信息科技有限公司）**

- 官网：[https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- 商业咨询微信：**zhuatech**、**zhuatech2**
- 服务：商业授权、定制开发、部署、系统集成与二次开发咨询。

| 微信 zhuatech | 微信 zhuatech2 |
|---|---|
| ![微信 zhuatech](docs/images/wechat-zhuatech.png) | ![微信 zhuatech2](docs/images/wechat-zhuatech2.png) |

品牌联系方式与源码许可证分别适用；商业使用需取得书面授权。
