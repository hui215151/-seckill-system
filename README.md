seckill-system/
├── README.md                 # 项目说明（最重要）
├── sql/
│   └── seckill.sql           # 数据库表结构
├── docs/
│   ├── architecture.png      # 架构图（自己画或找图）
│   ├── flowchart.png         # 秒杀流程图
│   └── jmeter-result.png     # 压测结果截图
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── seckill/
│       │           ├── controller/
│       │           ├── service/
│       │           ├── mapper/
│       │           ├── entity/
│       │           ├── config/          # Redis、RabbitMQ配置
│       │           ├── utils/           # 工具类
│       │           └── lua/             # Lua脚本
│       └── resources/
│           ├── application.yml
│           └── lua/
│               └── stock_deduct.lua     # 扣库存Lua脚本
└── pom.xml
