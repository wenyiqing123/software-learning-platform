-- Course reading and a reviewed objective question bank. No graph writes.
-- New content JSON is stored as text for portable MySQL/H2 consumption.
CREATE TABLE course_study_section (
 id BIGINT NOT NULL AUTO_INCREMENT, course_id BIGINT NOT NULL, chapter_id BIGINT NOT NULL,
 knowledge_point_id BIGINT NOT NULL, title VARCHAR(160) NOT NULL,
 objectives_json TEXT NOT NULL, content_json MEDIUMTEXT NOT NULL, resources_json TEXT NOT NULL,
 sort_order INT NOT NULL DEFAULT 0, PRIMARY KEY(id),
 UNIQUE KEY uk_study_section_point(knowledge_point_id),
 KEY idx_study_section_course(course_id,chapter_id,sort_order),
 CONSTRAINT fk_study_section_course FOREIGN KEY(course_id) REFERENCES course(id) ON DELETE CASCADE,
 CONSTRAINT fk_study_section_chapter FOREIGN KEY(chapter_id) REFERENCES course_chapter(id) ON DELETE CASCADE,
 CONSTRAINT fk_study_section_point FOREIGN KEY(knowledge_point_id) REFERENCES knowledge_point(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE course_study_progress (
 user_id BIGINT NOT NULL, section_id BIGINT NOT NULL, completed BOOLEAN NOT NULL DEFAULT FALSE,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(user_id,section_id),
 CONSTRAINT fk_study_progress_user FOREIGN KEY(user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
 CONSTRAINT fk_study_progress_section FOREIGN KEY(section_id) REFERENCES course_study_section(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO course_study_section (course_id,chapter_id,knowledge_point_id,title,objectives_json,content_json,resources_json,sort_order)
SELECT c.id,ch.id,kp.id,'从真实问题到可验证的需求',
 '["区分用户目标、功能需求与质量约束","为需求写出可观察的验收条件","通过访谈、原型与评审识别冲突和遗漏"]',
 '[{"heading":"先明确问题和边界","paragraphs":["需求分析回答系统为什么存在、为谁服务以及必须完成什么。访谈旅客、出票人员和财务人员时，先记录他们的业务目标与现有困难，再识别系统边界、外部系统和业务规则。用户提出“增加一个按钮”时，需要继续追问按钮要解决的任务，避免把某一种界面方案误当作需求本身。","机票预定的范围可以包括查询航班、保留座位、支付和取消订单；航空公司调度航班则可能属于外部系统。用术语表统一“预订成功”“已出票”和“已支付”的含义，能减少跨角色沟通中的歧义。"]},{"heading":"把需求写成能检查的约定","paragraphs":["功能需求描述输入、处理与可观察的结果，例如“用户选择航班后，系统校验剩余座位并生成待支付订单”。质量需求同样需要条件和度量：与其写“查询要快”，不如约定“在约定测试数据规模与并发条件下，95% 的查询在 2 秒内返回”。数值是项目协商的验收目标，不是对所有系统的通用规定。","每条需求分配稳定编号，记录来源、优先级和验收方式。评审时检查一致性、完整性、可行性和可验证性；遇到冲突先确定业务规则和决策者。需求发生变化后，通过变更记录和追踪关系分析对设计、测试与进度的影响。"]},{"heading":"动手检查","paragraphs":["为“旅客取消订单”写出正常流程、订单已出票和退款失败三种场景。分别指出谁发起操作、系统返回什么、哪些结果必须保存。完成后，用一条可执行的验收条件检查自己的表述是否仍含“及时”“友好”等无法直接验证的词。"]}]',
 '[{"title":"SWEBOK：软件工程知识体系","url":"https://www.computer.org/education/bodies-of-knowledge/software-engineering","description":"参考 Software Requirements 知识领域，结合本节整理需求获取、分析与验证的关系。"}]',1
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
JOIN course_chapter ch ON ch.id=kp.chapter_id AND ch.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='需求分析';

INSERT INTO course_study_section (course_id,chapter_id,knowledge_point_id,title,objectives_json,content_json,resources_json,sort_order)
SELECT c.id,ch.id,kp.id,'用数据流图解释业务数据如何变换',
 '["识别外部实体、加工、数据流和数据存储","检查父图与子图的输入输出平衡","发现黑洞、奇迹与不合理的直接数据流"]',
 '[{"heading":"四类元素各司其职","paragraphs":["数据流图描述数据从哪里来、经哪些加工变换、保存在哪里以及流向哪里。外部实体表示系统边界之外的数据来源或接收者；加工负责变换数据；箭头代表命名的数据流；数据存储表示需要保留的数据集合。加工通常用“动词＋宾语”命名，例如“校验预订请求”；数据流与存储通常使用名词短语。","在机票案例中，旅客把“查询条件”交给“查询可用航班”，加工读取“航班信息”并输出“可选航班列表”。一条箭头的名字应说明传输的数据内容，而不是“下一步”或“执行查询”这类控制动作。"]},{"heading":"分层与平衡","paragraphs":["上下文图把整个系统视为一个加工，先画清与外部实体交换的数据。逐层分解时，把一个加工展开成若干子加工，但父加工与外部交换的数据必须在子图边界上保持含义和内容上的平衡；内部可以增加中间流和数据存储。","检查加工是否只有输入没有输出，或者只有输出没有合理输入。外部实体不能绕过加工直接读写数据存储；两个存储之间的数据转换也应通过加工完成。数据字典补充箭头和存储的字段、取值范围及结构，不能只凭线条猜测数据含义。"]},{"heading":"动手检查","paragraphs":["画出“提交预订请求→校验座位→生成订单”的一层图。标注旅客输入、航班信息、订单存储和确认结果。再把“校验座位”分解成两个子加工，逐项比对子图边界的输入输出是否与原加工一致。"]}]',
 '[{"title":"OpenLearn：软件开发学习资料","url":"https://www.open.edu/openlearn/science-maths-technology/computing-ict","description":"可在开放课程中继续学习系统分析与建模，阅读时区分数据视角与控制视角。"}]',2
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
JOIN course_chapter ch ON ch.id=kp.chapter_id AND ch.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='数据流图DFD';

INSERT INTO course_study_section (course_id,chapter_id,knowledge_point_id,title,objectives_json,content_json,resources_json,sort_order)
SELECT c.id,ch.id,kp.id,'围绕参与者目标组织 UML 用例',
 '["从系统边界外识别参与者角色","区分用例图与流程或交互图的职责","正确解释 include 与 extend"]',
 '[{"heading":"参与者表示角色，用例表示目标","paragraphs":["用例图从外部使用者的角度描述系统提供什么服务。参与者是与系统交互的角色，可以是旅客、出票人员，也可以是支付平台等外部系统；它不等同于某一个具体自然人。系统边界框内放置“查询航班”“预订机票”等有业务价值的目标，边界外放置参与者。","用例图适合展示功能范围与角色关系，不负责表达每个消息的先后顺序、数据库表结构或方法实现。需要刻画交互时序时可使用顺序图；需要表达流程分支时可使用活动图。几种图表达不同视角，应使用一致术语相互补充。"]},{"heading":"用文本补足行为与例外","paragraphs":["一个可用的用例说明应记录触发条件、前置条件、主成功场景、替代流程和后置条件。以“支付订单”为例，主流程之外还应考虑支付取消、超时和结果重复通知。先把场景写清，再判断是否需要抽取公共行为。","include 表示在该包含点执行被包含用例的行为，常用于复用共同步骤；extend 在明确条件与扩展点下为基础用例增加可选行为。不要仅因为两个功能名称相似就建立关系，也不要把 include 当作一般的先后顺序箭头。"]},{"heading":"动手检查","paragraphs":["分别为旅客与出票人员列出两个目标。对“预订机票”写一个座位不足的替代流程，并说明系统状态最后如何变化。最后检查每个用例是否有清晰的参与者目标，而不是只有“点击按钮”这类界面动作。"]}]',
 '[{"title":"OMG UML 2.5.1 规范","url":"https://www.omg.org/spec/UML/2.5.1","description":"原始规范可用于核对 Actor、UseCase、Include 与 Extend 的定义。"}]',3
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
JOIN course_chapter ch ON ch.id=kp.chapter_id AND ch.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='用例图';

INSERT INTO course_study_section (course_id,chapter_id,knowledge_point_id,title,objectives_json,content_json,resources_json,sort_order)
SELECT c.id,ch.id,kp.id,'把需求规格变成设计与验收的共同依据',
 '["组织功能、接口、质量与约束需求","使用编号和追踪关系连接需求与测试","通过评审改进一致性和可验证性"]',
 '[{"heading":"规格说明书记录共同约定","paragraphs":["软件需求规格说明书（SRS）将经过确认的需求组织为可评审的基线。通常说明目的、范围、术语、用户与运行环境，然后按业务目标组织功能需求，并记录外部接口、数据要求、质量属性和约束。它服务于用户、开发人员与测试人员之间的协作，而不只是交付时的一份附件。","写法应清晰、一致、可追踪并可验证。把“系统应安全可靠”拆成有场景、条件和判定方式的需求，例如权限不足时拒绝访问并记录必要审计信息。技术限制属于约束；尚未决定的方案应明确标记待确认事项，避免把猜测写成已达成一致的规则。"]},{"heading":"从基线到变更影响分析","paragraphs":["为每条需求使用稳定标识，例如 REQ-BOOK-03，并建立与用例、设计和测试用例的对应关系。一条需求可能由多项测试验证，一项集成测试也可能覆盖多个需求。追踪关系帮助团队发现未验证需求，并在需求变更时定位受影响的设计和测试。","评审应邀请相关角色检查矛盾、遗漏与不可行约束。基线并不表示禁止变化，而表示变化有明确版本、原因、评估和批准记录。不要只更新正文而遗漏验收条件和测试，否则文档与实际行为很快会脱节。"]},{"heading":"动手检查","paragraphs":["给“用户只可取消自己的未出票订单”分配需求编号，补写前置条件、异常结果和验收测试。设想业务改为“已出票订单允许申请退票”，列出需求、状态模型、权限校验和测试中需要重新评估的项目。"]}]',
 '[{"title":"NASA Software Engineering Handbook","url":"https://swehb.nasa.gov/","description":"参考软件需求、需求验证与双向追踪相关条目，比较其检查项与课程案例。"}]',4
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
JOIN course_chapter ch ON ch.id=kp.chapter_id AND ch.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='软件需求规格说明书';

INSERT INTO course_study_section (course_id,chapter_id,knowledge_point_id,title,objectives_json,content_json,resources_json,sort_order)
SELECT c.id,ch.id,kp.id,'从逻辑数据集合走向一致的数据设计',
 '["解释 DFD 中的数据存储含义","区分逻辑存储、物理数据库与外部实体","借助数据字典和约束维护一致性"]',
 '[{"heading":"先描述保留什么，再选择怎么保存","paragraphs":["DFD 中的数据存储表示加工之间需要持久保留的数据集合，如“订单记录”或“航班信息”。它处于逻辑建模层面，不强制对应一个数据库、一张表或某种存储产品；同一个逻辑集合以后可能分布在多个表中。存储名称应反映数据内容，而不是读写动作。","加工从存储读取数据，或向存储写入经过校验的数据。DFD 中外部实体不能直接连接数据存储，因为边界内的数据处理和校验应通过加工表达。逻辑模型应说明输入输出数据含义，而不是提前用磁盘、索引等物理细节替代业务分析。"]},{"heading":"一致性来自明确的数据规则","paragraphs":["数据字典记录订单编号、旅客标识、航班标识、金额与订单状态等字段的含义、取值和结构。进入关系设计后再考虑主键、外键、唯一性与必要的事务边界，例如订单引用有效航班、同一支付事件不会重复入账。","并发预订中，先查询有座位再单独写订单可能产生竞争；设计时需要明确余票与订单状态的一致性策略。模型中的“订单存储”不会自动保证事务正确，仍需在实现与测试中验证约束、并发和失败恢复。"]},{"heading":"动手检查","paragraphs":["为“订单记录”写一份简短数据字典，区分业务编号和内部标识。画出“创建订单”与“取消订单”对它的读写，并检查每条数据流都连接了加工。再提出两条实现层面应验证的数据约束。"]}]',
 '[{"title":"SWEBOK：设计与数据相关知识","url":"https://www.computer.org/education/bodies-of-knowledge/software-engineering","description":"结合 Software Design 知识领域，辨析逻辑分析模型与实现决策。"}]',5
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
JOIN course_chapter ch ON ch.id=kp.chapter_id AND ch.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='数据存储';

INSERT INTO course_study_section (course_id,chapter_id,knowledge_point_id,title,objectives_json,content_json,resources_json,sort_order)
SELECT c.id,ch.id,kp.id,'按风险与反馈安排软件生命周期活动',
 '["说明需求、设计、实现、测试与维护的联系","比较顺序、迭代和增量交付的适用考虑","在每轮迭代中安排质量检查与配置管理"]',
 '[{"heading":"生命周期是一组持续关联的活动","paragraphs":["软件生命周期覆盖构思与问题定义、需求、设计、实现、验证、部署、运行维护直至退役。真实项目中的活动存在反馈：测试可能发现需求歧义，运行事件可能触发设计修改。阶段名称帮助组织工作，不能据此认为测试只在编码全部结束后才发生。","需求说明系统要解决的问题；设计确定结构、接口和关键决策；实现将设计落实为代码；验证检查工作产品是否满足规定要求，确认关注系统能否满足预期使用。评审、测试、配置管理和质量保证可以贯穿多个阶段。"]},{"heading":"选择过程时考虑变化与风险","paragraphs":["顺序式安排适合需求相对稳定、交付阶段明确的情形，但晚期反馈可能增加返工。迭代通过重复活动逐步改进已有方案，增量通过分批增加可用能力逐步交付；二者经常结合。原型能帮助澄清未知需求，螺旋式过程强调围绕风险安排循环。","机票案例可以先实现查询与模拟订单，再增量加入支付、取消与恢复。每轮都应有可检查的目标、版本和验收证据，并评估并发、一致性及外部接口风险。选择迭代并不意味着省略文档或测试，选择阶段交付也不意味着拒绝反馈。"]},{"heading":"动手检查","paragraphs":["为四周课程项目安排两次可演示增量，写明每次提供的业务能力、主要风险和验收方式。标出一个可能因第一轮反馈而调整的需求，以及对应的变更和回归测试安排。"]}]',
 '[{"title":"SWEBOK：软件工程过程","url":"https://www.computer.org/education/bodies-of-knowledge/software-engineering","description":"参照 Software Engineering Process 与质量领域，梳理生命周期活动之间的关系。"}]',6
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
JOIN course_chapter ch ON ch.id=kp.chapter_id AND ch.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='软件生命周期';

INSERT INTO course_study_section (course_id,chapter_id,knowledge_point_id,title,objectives_json,content_json,resources_json,sort_order)
SELECT c.id,ch.id,kp.id,'让交付后的改变保持可控',
 '["区分纠正性、适应性、完善性与预防性维护","对变更做影响分析并安排回归测试","通过版本和回退方案降低上线风险"]',
 '[{"heading":"维护不只是修复故障","paragraphs":["软件交付后仍需应对缺陷、环境变化和新的业务需要。纠正性维护修复已发现的问题；适应性维护应对操作系统、外部接口或法规环境的变化；完善性维护改善性能或扩展功能；预防性维护降低潜在故障和未来修改的风险。同一项变更可能包含多种动机，应说明其主要目的。","支付平台升级协议，需要调整对接方式，主要属于适应性维护；修复订单金额计算错误，主要属于纠正性维护。维护工作常常需要重新分析需求与设计，因此良好的模块边界、自动化测试、配置与文档会影响长期成本。"]},{"heading":"用证据控制变更风险","paragraphs":["维护开始前先重现问题或澄清变更目标，结合需求追踪、模块依赖和数据约束分析影响。建立一个覆盖问题的检查，再实施最小必要修改，并运行与受影响功能相关的回归测试。不能只验证新路径，而忽视原来已经可用的流程。","涉及订单和支付状态时，考虑历史数据、重复事件与版本兼容。发布需要说明版本、验证结果、监测信号和回退步骤。维护记录应能回答为什么改、改了什么、如何验证，以支持之后的追踪与问题定位。"]},{"heading":"动手检查","paragraphs":["把“修复退款重复入账”“适配新的支付接口”“优化航班搜索响应时间”按主要动机分类。任选一项，列出受影响模块、必须保留的既有行为，以及上线后用于判断变更是否成功的观察指标。"]}]',
 '[{"title":"SWEBOK：软件维护","url":"https://www.computer.org/education/bodies-of-knowledge/software-engineering","description":"使用 Software Maintenance 知识领域核对维护分类、变更与质量活动。"}]',7
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
JOIN course_chapter ch ON ch.id=kp.chapter_id AND ch.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='软件维护';

INSERT INTO course_study_section (course_id,chapter_id,knowledge_point_id,title,objectives_json,content_json,resources_json,sort_order)
SELECT c.id,ch.id,kp.id,'贯穿需求、建模与验证的机票预订案例',
 '["把业务场景映射到用例、数据流和存储","识别座位竞争、重复通知与状态异常","从需求推导正常和异常验收场景"]',
 '[{"heading":"先对齐场景，再连接不同模型","paragraphs":["设定范围为航班查询、创建待支付订单、支付确认与取消。旅客、出票人员和支付平台是不同参与者；航班管理可由外部系统提供。需求给出业务规则，用例描述参与者目标，DFD 表达查询条件、订单与支付结果如何在加工和存储间流动，数据字典统一这些数据的含义。","以创建订单为例，旅客提交航班与乘机人信息，系统校验输入和可用座位，然后记录待支付订单并返回编号。DFD 可以使用“校验预订请求”“生成订单”等加工；用例说明补充座位不足、输入错误和外部系统不可用等替代流程。"]},{"heading":"用状态与约束处理失败","paragraphs":["为订单定义待支付、已支付、已取消等状态，并明确允许的转换。支付通知可能重复或延迟，因此同一事件需要幂等处理，并检查订单、金额与支付方身份。取消与支付并发时，必须明确冲突解决规则；一张图本身不能替代事务设计和业务决策。","余票不足属于预期业务结果；支付超时可能意味着结果未知，不能简单等同于支付失败。先记录可追踪的请求和状态，再查询确认或安排补偿，避免重试制造重复订单或重复扣款。该案例用于逻辑分析，不要求对真实航空或支付系统执行操作。"]},{"heading":"把模型转换成验收证据","paragraphs":["为“创建订单”至少设计成功、余票不足和重复提交三种验收情形，为“处理支付结果”加入重复通知与金额不匹配情形。每项检查应关联需求编号、必要的初始数据和期望状态。","回到知识图谱查看需求、DFD、用例和数据存储之间的概念联系，再完成本节练习。阅读完成记录只代表完成小节学习；是否掌握仍应结合练习表现和复习结果判断。"]}]',
 '[{"title":"OMG UML：统一建模语言规范","url":"https://www.omg.org/spec/UML/2.5.1","description":"按需用用例、活动与交互图补充案例模型，并在不同图之间保持术语一致。"}]',8
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
JOIN course_chapter ch ON ch.id=kp.chapter_id AND ch.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='机票预定系统';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'SINGLE','下列哪条需求最容易通过明确的验收测试验证？',
 '[{"key":"A","text":"系统界面应非常友好"},{"key":"B","text":"在约定并发与数据规模下，95% 的航班查询在 2 秒内返回"},{"key":"C","text":"系统应采用最先进的技术"},{"key":"D","text":"系统运行必须始终完美"}]','["B"]','需求需要给出条件、度量和可判定的结果。明确测试负载下的响应时间目标可被验证；“友好”“先进”“完美”缺少验收准则。',2,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='需求分析';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'MULTIPLE','需求评审中，哪些做法有助于发现问题？',
 '[{"key":"A","text":"邀请业务相关者确认术语和边界"},{"key":"B","text":"检查不同需求之间是否冲突"},{"key":"C","text":"把尚未确认的假设直接当作事实"},{"key":"D","text":"为主要需求检查可验证的验收条件"}]','["A","B","D"]','共同术语、冲突检查和验收条件能暴露歧义与遗漏。未确认假设应标记并核实，不能直接充当已确认的需求。',2,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='需求分析';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'TRUE_FALSE','需求获取时，应先理解用户的业务目标，而不是把用户提出的某个界面方案直接视为唯一需求。',
 '[{"key":"TRUE","text":"正确"},{"key":"FALSE","text":"错误"}]','["TRUE"]','同一个业务目标可能有多种解决方案。先澄清目标、约束和验收条件，才能评估具体方案是否合适。',1,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='需求分析';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'SINGLE','将 DFD 中的一个加工分解为子图时，应优先检查什么？',
 '[{"key":"A","text":"子图是否恰好包含四个加工"},{"key":"B","text":"父图与子图边界的输入输出数据是否平衡"},{"key":"C","text":"所有存储是否都改成外部实体"},{"key":"D","text":"子图箭头是否标明执行顺序"}]','["B"]','分层分解要求保持父加工与外部交换数据的含义和内容。子图可引入内部流与存储，但不能无故增加或遗漏边界数据。',2,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='数据流图DFD';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'MULTIPLE','以下哪些连接符合基本 DFD 建模规则？',
 '[{"key":"A","text":"外部实体向加工提供数据"},{"key":"B","text":"加工向数据存储写入数据"},{"key":"C","text":"外部实体直接修改数据存储"},{"key":"D","text":"两个数据存储直接交换数据而无加工"}]','["A","B"]','数据的处理与转换通过加工表达。外部实体与存储、存储与存储之间不应绕过加工直接交换数据。',2,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='数据流图DFD';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'TRUE_FALSE','数据流图中的箭头主要表示程序控制语句的执行顺序。',
 '[{"key":"TRUE","text":"正确"},{"key":"FALSE","text":"错误"}]','["FALSE"]','DFD 的箭头表示有名称的数据流。控制顺序、循环与时序需要用适当的流程、活动或交互模型表达。',1,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='数据流图DFD';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'SINGLE','在软件系统的 UML 用例图中，“支付平台”可以作为参与者的条件是什么？',
 '[{"key":"A","text":"它是系统内部的数据库表"},{"key":"B","text":"它在系统边界外与系统交互"},{"key":"C","text":"它属于系统内部的一个方法"},{"key":"D","text":"它是用例框中的任意文字"}]','["B"]','参与者表示系统边界之外的交互角色，既可以是人的角色，也可以是外部系统。',1,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='用例图';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'MULTIPLE','下列关于用例及其关系的表述，哪些正确？',
 '[{"key":"A","text":"用例通常围绕参与者希望实现的目标组织"},{"key":"B","text":"include 可用于复用在包含点执行的共同用例行为"},{"key":"C","text":"extend 可在规定条件与扩展点增加行为"},{"key":"D","text":"用例图能够完整替代接口时序和数据库设计"}]','["A","B","C"]','用例围绕外部目标，include 与 extend 有各自的语义。用例图不负责完整说明时序或数据库结构，需要其他模型和文本补充。',3,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='用例图';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'TRUE_FALSE','用例说明中的替代流程可用于记录座位不足或支付失败等异常业务情形。',
 '[{"key":"TRUE","text":"正确"},{"key":"FALSE","text":"错误"}]','["TRUE"]','用例文本应补充主成功场景以外的重要分支，并明确异常发生后的结果与状态。',1,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='用例图';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'SINGLE','为需求 REQ-BOOK-03 建立到设计项与测试用例的对应关系，主要有什么作用？',
 '[{"key":"A","text":"保证需求永远不会变化"},{"key":"B","text":"支持覆盖检查与变更影响分析"},{"key":"C","text":"取代所有需求评审"},{"key":"D","text":"自动生成没有缺陷的代码"}]','["B"]','需求追踪关系帮助发现未验证需求，并在需求改变时定位相关设计和测试。它不能代替评审或保证无缺陷。',2,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='软件需求规格说明书';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'MULTIPLE','软件需求规格说明书中，通常需要明确哪些内容？',
 '[{"key":"A","text":"系统功能与外部接口"},{"key":"B","text":"可验证的质量要求与约束"},{"key":"C","text":"经确认的数据与业务规则"},{"key":"D","text":"所有类的最终实现代码"}]','["A","B","C"]','SRS 描述经过确认的软件需求，包括功能、接口、质量、约束和数据规则。完整实现代码属于实现产物。',2,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='软件需求规格说明书';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'TRUE_FALSE','需求文档建立基线后，任何业务变化都不允许再修改需求。',
 '[{"key":"TRUE","text":"正确"},{"key":"FALSE","text":"错误"}]','["FALSE"]','基线提供受控变更的起点。合理变化仍可经过记录、影响分析与批准更新，并同步相关设计和测试。',1,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='软件需求规格说明书';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'SINGLE','DFD 中的“订单记录”数据存储表示什么？',
 '[{"key":"A","text":"必须使用某一厂商数据库的一张表"},{"key":"B","text":"需要保留的一组逻辑数据"},{"key":"C","text":"专门控制程序跳转的指令"},{"key":"D","text":"处于系统边界外的一个参与者"}]','["B"]','逻辑数据存储描述需要保留的数据集合，不强制对应某种数据库产品或一张物理表。',1,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='数据存储';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'MULTIPLE','数据字典可以帮助明确哪些信息？',
 '[{"key":"A","text":"“订单编号”等数据项的含义"},{"key":"B","text":"数据结构及字段取值约束"},{"key":"C","text":"同一术语在不同数据流中的一致含义"},{"key":"D","text":"操作系统线程的精确执行时序"}]','["A","B","C"]','数据字典补充数据项、结构与约束，帮助不同模型共享一致术语；它不负责描述线程调度。',2,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='数据存储';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'TRUE_FALSE','在 DFD 中，数据存储之间的数据转换通常应通过加工表达。',
 '[{"key":"TRUE","text":"正确"},{"key":"FALSE","text":"错误"}]','["TRUE"]','加工表示数据变换。存储之间不应以未经加工的直接流代替应说明的处理。',1,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='数据存储';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'SINGLE','团队先交付航班查询，随后加入预订和退款功能，这主要体现什么？',
 '[{"key":"A","text":"只允许一次性交付"},{"key":"B","text":"按增量逐步交付可用能力"},{"key":"C","text":"所有需求均不必分析"},{"key":"D","text":"维护发生在需求分析之前且只发生一次"}]','["B"]','增量交付按批次增加可用能力，每个增量仍需必要的需求、设计、实现与验证活动。',2,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='软件生命周期';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'MULTIPLE','关于迭代开发与质量活动，下列哪些表述正确？',
 '[{"key":"A","text":"早期反馈可能促使需求或设计调整"},{"key":"B","text":"每轮迭代都可以安排测试与评审"},{"key":"C","text":"迭代开发意味着无需版本和配置管理"},{"key":"D","text":"风险分析可影响下一轮迭代的重点"}]','["A","B","D"]','迭代利用反馈改进工作，质量与配置活动仍需持续开展，风险可用于安排工作优先级。',2,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='软件生命周期';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'TRUE_FALSE','只有等所有编码结束后才能开展任何测试相关工作。',
 '[{"key":"TRUE","text":"正确"},{"key":"FALSE","text":"错误"}]','["FALSE"]','需求评审、验收条件与测试设计可在编码前开展；组件、集成和回归测试也可以随迭代持续进行。',1,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='软件生命周期';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'SINGLE','为兼容支付平台新版本协议而修改接口，主要属于哪类维护？',
 '[{"key":"A","text":"纠正性维护"},{"key":"B","text":"适应性维护"},{"key":"C","text":"仅与界面美化相关的维护"},{"key":"D","text":"不属于软件维护"}]','["B"]','适应性维护使软件继续适用于变化的环境；对接外部协议升级是典型情形。',2,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='软件维护';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'MULTIPLE','修改已上线的退款逻辑时，哪些措施合理？',
 '[{"key":"A","text":"先分析相关需求、状态和模块的影响"},{"key":"B","text":"为已发现问题增加可复现检查"},{"key":"C","text":"只测试新代码而完全跳过旧流程"},{"key":"D","text":"对受影响的既有行为开展回归测试"}]','["A","B","D"]','维护需要明确影响、用检查重现问题并验证修复，同时通过回归测试保护既有行为。',2,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='软件维护';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'TRUE_FALSE','软件交付后的维护工作只包括修复已发现的缺陷。',
 '[{"key":"TRUE","text":"正确"},{"key":"FALSE","text":"错误"}]','["FALSE"]','维护还可能为了适应环境、完善性能或功能，以及预防潜在问题。',1,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='软件维护';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'SINGLE','同一笔支付结果可能被重复通知，订单系统应优先具备哪种处理能力？',
 '[{"key":"A","text":"每次通知都再扣一次款"},{"key":"B","text":"依据事件或业务标识进行幂等处理"},{"key":"C","text":"只依赖前端禁用按钮"},{"key":"D","text":"忽略所有支付结果"}]','["B"]','重复事件不应重复产生业务效果。后端需要校验通知并结合唯一标识、订单状态与一致性约束实现幂等处理。',3,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='机票预定系统';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'MULTIPLE','对“创建机票预订订单”进行验收时，哪些场景值得覆盖？',
 '[{"key":"A","text":"有可用座位时成功创建订单"},{"key":"B","text":"余票不足时返回明确结果且不产生无效订单"},{"key":"C","text":"重复提交时按约定处理而不意外重复预订"},{"key":"D","text":"只检查按钮颜色即可替代业务验收"}]','["A","B","C"]','验收应覆盖正常、边界和重要异常路径，并验证订单与座位等业务结果。按钮颜色不能替代业务行为验证。',2,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='机票预定系统';

INSERT INTO quiz_question (course_id,knowledge_point_id,question_type,stem,options_json,answer_json,explanation,difficulty,source)
SELECT c.id,kp.id,'TRUE_FALSE','支付请求超时就能确定支付一定失败，可以不再核实订单支付结果。',
 '[{"key":"TRUE","text":"正确"},{"key":"FALSE","text":"错误"}]','["FALSE"]','超时可能只是结果尚未返回，实际支付结果可能未知。应查询确认或采用可追踪的恢复机制，避免重试产生重复扣款或错误状态。',3,'COURSE_CONTENT_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='机票预定系统';

-- Stage numeric prerequisite IDs to avoid same-table update restrictions.
CREATE TABLE course_content_seed_prerequisite (point_id BIGINT NOT NULL PRIMARY KEY, prerequisite_ids VARCHAR(200) NOT NULL);
INSERT INTO course_content_seed_prerequisite (point_id,prerequisite_ids)
SELECT kp.id, CONCAT('[',p0.id,']')
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
JOIN knowledge_point p0 ON p0.course_id=c.id AND p0.graph_entity='需求分析'
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='数据流图DFD';
INSERT INTO course_content_seed_prerequisite (point_id,prerequisite_ids)
SELECT kp.id, CONCAT('[',p0.id,']')
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
JOIN knowledge_point p0 ON p0.course_id=c.id AND p0.graph_entity='需求分析'
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='用例图';
INSERT INTO course_content_seed_prerequisite (point_id,prerequisite_ids)
SELECT kp.id, CONCAT('[',p0.id,']')
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
JOIN knowledge_point p0 ON p0.course_id=c.id AND p0.graph_entity='需求分析'
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='软件需求规格说明书';
INSERT INTO course_content_seed_prerequisite (point_id,prerequisite_ids)
SELECT kp.id, CONCAT('[',p0.id,']')
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
JOIN knowledge_point p0 ON p0.course_id=c.id AND p0.graph_entity='数据流图DFD'
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='数据存储';
INSERT INTO course_content_seed_prerequisite (point_id,prerequisite_ids)
SELECT kp.id, CONCAT('[',p0.id,']')
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
JOIN knowledge_point p0 ON p0.course_id=c.id AND p0.graph_entity='软件生命周期'
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='软件维护';
INSERT INTO course_content_seed_prerequisite (point_id,prerequisite_ids)
SELECT kp.id, CONCAT('[',p0.id,',',p1.id,']')
FROM course c JOIN knowledge_point kp ON kp.course_id=c.id
JOIN knowledge_point p0 ON p0.course_id=c.id AND p0.graph_entity='需求分析'
JOIN knowledge_point p1 ON p1.course_id=c.id AND p1.graph_entity='数据流图DFD'
WHERE c.code='SE-2026-FALL' AND c.term='2026 秋季' AND kp.graph_entity='机票预定系统';
UPDATE knowledge_point SET prerequisites_json=(SELECT prerequisite_ids FROM course_content_seed_prerequisite seed WHERE seed.point_id=knowledge_point.id)
WHERE id IN (SELECT point_id FROM course_content_seed_prerequisite);
DROP TABLE course_content_seed_prerequisite;
