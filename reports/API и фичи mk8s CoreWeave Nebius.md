# Nebius и CoreWeave расходятся там, где живут узлы

Главное различие между Nebius Managed Service for Kubernetes (mk8s) и CoreWeave Kubernetes Service (CKS) не в транспорте, а в том, где находится управление узлами. У Nebius кластер и группы узлов — объекты одного облачного gRPC-API `nebius.mk8s.v1`. Каждая мутация возвращает долгоживущую `Operation`; есть оптимистическая блокировка через `resource_version`, ключ идемпотентности `X-Idempotency-Key` и маска сброса `X-ResetMask`. У CoreWeave облачный API `coreweave.cks.v1beta1` (Connect, gRPC или REST/JSON) управляет только кластером и VPC. Он сразу возвращает объект, статус приходится опрашивать, а ни идемпотентности, ни версий ресурса нет. Пулы узлов в облачный API у CoreWeave вообще не входят: это CRD `NodePool` (`compute.coreweave.com/v1alpha1`) внутри самого кластера, поэтому автоматизации нужны два контура управления и два набора учётных данных. По продукту CKS — это bare metal без гипервизора с NVIDIA GPU Operator под управлением платформы. Mission Control раз в час гоняет активную проверку простаивающих узлов (HPC Verification) и сам заменяет неисправные, но опубликованного SLA на control plane нет. mk8s — это виртуальные машины и бесплатный HA control plane с SLA 99,9%. Клиент выбирает между образами с предустановленными драйверами и собственным GPU Operator; пассивные GPU-проверки идут каждые 5 минут, а активные доступны только в preview по запросу. Общего больше, чем кажется:
- control plane у обоих бесплатный;
- версии обновляются по схеме «сначала control plane, потом узлы»;
- узлы масштабирует upstream Cluster Autoscaler, и у обоих он запрещён для стоек NVL72 из 18 узлов;
- CNI — Cilium;
- Slurm поверх Kubernetes есть у обоих (Soperator и SUNK);
- в сентябре 2026 г. оба получили Platinum в ClusterMAX 3.0.

Для инженера, который пишет общий инструмент, вывод прямой: абстракция должна выравнивать противоположные семантики обновления (полная замена против частичного PATCH), разные модели асинхронности и разный словарь сигналов о здоровье узлов.

> **Как читать источники.** docs.nebius.com, nebius.com и docs.coreweave.com были заблокированы в среде исследования. Поэтому факты со ссылками на эти домены и на coreweave.com взяты из поисковых выдержек и надёжны в меньшей степени. Ссылки на github.com и buf.build ведут на proto-файлы, SDK и исходники Terraform-провайдеров, прочитанные напрямую; это самый надёжный слой. Пометка **[расхождение]** означает, что источники противоречат друг другу. **[не проверено]** означает, что факт взят из стороннего источника или выведен косвенно. **[гипотеза]** — собственная интерпретация. Сводка спорных мест — в отдельном разделе ниже.

## API: один gRPC-сервис у Nebius против двух контуров у CoreWeave

### Транспорт и зрелость: gRPC `v1` против Connect/REST `v1beta1`

Nebius публикует mk8s как **чистый gRPC/protobuf-API** на хосте `mk8s.api.nebius.cloud:443`. Оба сервиса объявляют `api_service_name = "mk8s"`, а тот же хост обслуживает общий `OperationService` ([nebius/api README](https://github.com/nebius/api/blob/main/README.md); [endpoints.md](https://github.com/nebius/api/blob/main/endpoints.md)). В mk8s-протах нет аннотаций `google.api.http`, поэтому документированного REST-пути не существует. Интегрироваться можно только через gRPC либо через SDK, CLI и Terraform, которые сами работают поверх gRPC. Текущая версия — `nebius.mk8s.v1`, де-факто GA. `nebius.mk8s.v1alpha1` с 10 ноября 2025 г. помечена `deprecated` с указанием «migrate to v1», но дата удаления не задана ([v1alpha1 cluster_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1alpha1/cluster_service.proto)). В имени хоста нет региона: размещение определяется проектом-родителем и routing code внутри идентификаторов.

CoreWeave описывает CKS в protobuf и публикует схемы как Buf-модули `buf.build/coreweave/cks` и `buf.build/coreweave/networking`. Обслуживает их `https://api.coreweave.com` **сразу тремя способами**: Connect RPC, gRPC и REST/JSON через транскодирование `google.api.http` (`GET/POST /v1beta1/cks/clusters`, `GET/PATCH/DELETE /v1beta1/cks/clusters/{id}`) ([CKS API docs](https://docs.coreweave.com/products/cks/reference/cks-api); [buf.build/coreweave/cks](https://buf.build/coreweave/cks)). Имена JSON-полей — в lowerCamelCase (`vpcId`, `podCidrName`), а официальный Terraform-провайдер обращается к API через Connect-клиенты ([provider client.go](https://github.com/coreweave/terraform-provider-coreweave/blob/main/coreweave/client.go)). Рабочая версия — **`v1beta1`**. В модуле уже лежит пакет `v1beta2`, переработанный в стиле Google AIP:
- канонические имена-UUID и `display_name`;
- клиентский `cluster_id`;
- `request_id` для идемпотентности;
- CEL-`filter` в List;
- k8s-подобные `conditions`.

Ни в документации, ни в провайдере его нет; работает ли он на проде — **[не проверено]** ([buf.build/coreweave/cks](https://buf.build/coreweave/cks)). Оба провайдера валидируют запросы правилами `buf.validate` (CEL) прямо в протах, и ни один не публикует политику стабильности или сроков депрекации.

### Модель ресурсов: иерархия проектов против плоского зонального объекта

У Nebius каждый ресурс устроен как `{metadata, spec, status}`, где `ResourceMetadata` несёт `id`, `parent_id`, `name`, `resource_version`, `created_at`, `updated_at` и `labels` ([metadata.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/metadata.proto)). Иерархия строгая: **tenant → project → `mk8scluster` → `mk8snodegroup`**. Родитель кластера — проект, родитель группы узлов — кластер ([cluster.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto); [node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)). Идентификаторы — типизированные NID вида `<type>-<3-символьный routing code><weak id>`, например `project-e00…` ([gosdk nid/patterns.go](https://github.com/nebius/gosdk/blob/main/nid/patterns.go); [README](https://github.com/nebius/api/blob/main/README.md)). Имя кластера — от 1 до 63 символов из букв, цифр, `-` и `_`; имя группы узлов допускает ещё и точку ([cluster_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster_service.proto)). Семантику полей машинно-читаемо задают аннотации ([annotations.proto](https://github.com/nebius/api/blob/main/nebius/annotations.proto)):
- `IMMUTABLE`;
- `OUTPUT_ONLY`;
- `MEANINGFUL_EMPTY_VALUE` — пустое сообщение включает функцию, null выключает;
- `NON_EMPTY_DEFAULT`.

В перечислении состояний нет ERROR — только `PROVISIONING`, `RUNNING` и `DELETING`. Сбои видны через операции, поле `events` с уровнем `ERROR` и флаг `reconciling` ([cluster.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto); [resource_event.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/resource_event.proto)).

`ClusterSpec` у Nebius намеренно минимален ([cluster.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto)). В `control_plane` лежат:
- версия в формате `MAJOR.MINOR`;
- неизменяемый `subnet_id`;
- опциональный публичный endpoint со списком `allowed_cidrs` — до **8 IPv4-CIDR**, с июня 2026 г.;
- `etcd_cluster_size`, 1 или 3 (по умолчанию 3);
- опциональные `audit_logs` и `karpenter`.

В `kube_network` — ровно один неизменяемый `service_cidrs` от /12 до /28, по умолчанию /16. **Поля pod CIDR нет вообще.** Нет и настроек CNI, OIDC API-сервера, аддонов или feature gates. Адресация подов задаётся на узел через `max_pods` (8–256, по умолчанию 110, то есть /24 на узел) ([node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)). IPv6 в API не представлен. В статусе возвращаются фактическая версия вида `1.30.0-nebius-cp.3`, публичный и приватный endpoint и CA-сертификат.

CoreWeave строит **плоский объект без иерархии**. `Cluster` в v1beta1 содержит такие поля ([buf.build/coreweave/cks](https://buf.build/coreweave/cks)):
- `id`;
- `name` — от 1 до 30 символов, `^[a-z0-9]([-a-z0-9]*[a-z0-9])?$`;
- `zone`, например `US-EAST-04A`;
- `vpc_id`, `public`, `version` (`"v1.32"`), `network`;
- `oidc`, `authn_webhook`, `authz_webhook`;
- `audit_policy` в base64;
- `kubelet` — переопределения KubeletConfiguration, требующие перезагрузки узлов;
- `additional_server_sans` — до 10;
- `tailscale` и `disable_oidc_info_publication`;
- выходные поля `status`, `api_server_endpoint` и `is_upgradeable`.

Проектов и папок нет: организация определяется токеном, а расположение — одной строкой зоны. Кластер и VPC зональны и обязаны находиться в одной AZ. Статусов зато больше: помимо `CREATING`, `UPDATING`, `RUNNING`, `DELETING` и `DELETED` есть `ERROR`, `FAILED`, `UPGRADING`, `UPGRADE_FAILED` и `PREEMPTED`; в последнем разрешено только удаление ([buf.build/coreweave/cks](https://buf.build/coreweave/cks)). Чем `ERROR` отличается от `FAILED`, не задокументировано.

Сеть CoreWeave моделирует гораздо подробнее. Кластер хранит не CIDR, а **имена префиксов**. Сами значения живут в отдельном объекте `coreweave.networking.v1beta1.VPC` ([buf.build/coreweave/networking](https://buf.build/coreweave/networking)):
- именованные `vpc_prefixes` — pod, service и internal LB;
- `host_prefixes` типов `PRIMARY`/`ROUTED`/`ATTACHED` с политикой IPAM;
- `ingress`, `egress` и `dhcp`.

Имена префиксов допускают пробелы (`"pod cidr"`). Поддерживается dual-stack IPv6, причём все три `_v6`-поля задаются вместе. Мутабельность асимметрична ([TF cks_cluster docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/resources/cks_cluster.md)):
- `internal_lb_cidr_names` можно только дополнять;
- диапазон NodePort можно только расширять;
- смена имени, зоны, VPC и pod/service CIDR требует пересоздания кластера.

### Узлы: `NodeGroup` в облачном API против CRD `NodePool` в кластере

У Nebius `NodeGroupSpec` состоит из `version`, взаимоисключающих `fixed_node_count` или `autoscaling{min_node_count, max_node_count}` (оба **0–100**), а также `template`, `strategy` и `auto_repair` ([node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)). Шаблон описывает виртуальную машину:
- платформу и пресет;
- загрузочный диск, с шифрованием с сентября 2026 г.;
- ОС;
- `gpu_settings` с `drivers_preset` и `dra`;
- `gpu_cluster` для InfiniBand;
- неизменяемый `nvlink.nvl_instance_group_id`;
- единственный сетевой интерфейс с публичным IP и security groups;
- файловые системы, cloud-init и сервисный аккаунт;
- `reservation_policy`, `local_disks` и `max_pods`;
- `pricing_model` (`on_demand`, `follows_spot_price`, `spot_pricing_policy`), с 22 сентября 2026 г.

CEL-правила запрещают автомасштабирование для групп с NVLink и требуют для них `max_surge = 0`. Важная тонкость: **изменения Kubernetes-меток и taints в шаблоне не применяются к существующим узлам** и не запускают раскатку. Остальные изменения шаблона пересоздают узлы согласно стратегии. Сама стратегия в Q3 2026 сменила значения по умолчанию ([node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto); [commit 5995ef7](https://github.com/nebius/api/commit/5995ef7)):
- `max_unavailable`: 0 → 1;
- `max_surge`: 1 → 0;
- `drain_timeout`: 0 → 10m.

Фактические значения публикуются в `status.strategy`. Статус группы считает `target_node_count`, `node_count`, `outdated_node_count` и `ready_node_count`.

API Nebius растёт почти ежемесячно. С января по сентябрь 2026 г. в нём появились `audit_logs`, `reservation_policy`, `karpenter`, `local_disks`, `max_pods`, `allowed_cidrs`, `security_groups`, `nvlink`, `disk_encryption`, DRA, `PreflightCheck` и `pricing_model` ([история nebius/api](https://github.com/nebius/api/commits/main/nebius/mk8s/v1)). При этом официальные Terraform-примеры выставляют атрибуты, которых нет в публичных протах: `template.underlay_required` и `preemptible = {on_preemption = "STOP", priority = 3}`, хотя в proto `PreemptibleSpec` пуст **[расхождение]** ([k8s-training/main.tf](https://github.com/nebius/nebius-solution-library/blob/main/k8s-training/main.tf)). Значит, схема провайдера генерируется из более богатого, вероятно внутреннего, описания, и рассчитывать на эти поля в чистом gRPC нельзя.

У CoreWeave пулов узлов **нет ни в облачном API, ни в Buf-модулях, ни в Terraform-провайдере**. Собственный репозиторий CoreWeave reference-architecture прямо пишет «CoreWeave has no Terraform nodepool resource» и создаёт пулы через `kubernetes_manifest` ([reference-architecture nodepool/main.tf](https://github.com/coreweave/reference-architecture/blob/main/terraform/modules/nodepool/main.tf)). `NodePool` — это CRD `compute.coreweave.com/v1alpha1`; судя по примерам, он кластерного уровня. Его spec содержит ([Node Pool reference](https://docs.coreweave.com/products/cks/reference/node-pool); [Create a Node Pool](https://docs.coreweave.com/products/cks/nodes/create)):
- обязательный и неизменяемый `instanceType`, например `gd-8xh100ib-i128`;
- `computeClass`;
- взаимоисключающие `targetNodes` и `targetRacks` (последний — только для стоек NVL72 по 18 узлов);
- `autoscaling`, `minNodes` и `maxNodes`;
- `prefill`;
- `nodeLabels`, `nodeAnnotations` и `nodeTaints`;
- `lifecycle.scaleDownStrategy` (`IdleOnly` или `PreferIdle`);
- `nodeConfigurationUpdateStrategy` (`Manual`, `OnSpecUpdate` или `Always`).

Масштабируют пул через subresource `scale` (`kubectl scale nodepool <name> --replicas N`). Состояние читают из условий `Validated`, `Capacity`, `Quota` и `AtTarget` и из поля `currentNodes` ([Manage Node Pools](https://docs.coreweave.com/products/cks/nodes/manage); [Node Pool status](https://docs.coreweave.com/products/cks/nodes/nodepool-status)). Ключевая семантика: квота проверяется при каждой установке `targetNodes`. Если цель её превышает, **пул молча заполняется до квоты**: при цели 15 и квоте 10 получится 10 узлов без всякой ошибки ([Manage Node Pools](https://docs.coreweave.com/products/cks/nodes/manage)). Жизненный цикл отдельных узлов выражен метками на объектах `Node` (`node.coreweave.cloud/state`, `/pending-state`, `/reserved`) и аннотацией `node.coreweave.cloud/cordonReason` ([Node state transitions](https://docs.coreweave.com/platform/fleet-management/node-lifecycle/state-transitions); [Node cordoning](https://docs.coreweave.com/products/cks/nodes/cordon)). Версиями образа, драйвера и Kubernetes на узлах управляют через «node profile» и команды cwic `nodepool upgrade`, `rollback` и `rollout` ([cwic README](https://github.com/coreweave/cwic/blob/main/README.md)).

Отсюда жёсткое следствие для автоматизации у CoreWeave. Пул нельзя создать, пока не выполнены три условия:
- кластер дошёл до `RUNNING`;
- получен kubeconfig;
- endpoint доступен по сети; для приватного кластера — через Tailscale или изнутри VPC.

Асинхронность здесь level-triggered, как в Kubernetes: желаемое число узлов плюс условия, без объекта операции.

### Операции и обновления: `Operation` с `X-ResetMask` против опроса статуса и `FieldMask`

Каждая мутация в mk8s возвращает `nebius.common.v1.Operation`. Операция **завершена, когда задано поле `status`** (`google.rpc.Status`); её опрашивают через `OperationService.Get` на том же хосте ([operation.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/operation.proto); [README](https://github.com/nebius/api/blob/main/README.md)). Прогресс виден в `progress_tracker` (такты, шаги, оценка завершения) и в mk8s-специфичном `ProgressData{problems}`. Прото прямо предупреждает: при нехватке квоты раскатка не падает, а «зависает» с ошибкой, видимой в `progress_data` ([node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto); [progress_data.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/progress_data.proto)). Параллельные операции над одним ресурсом не поддерживаются: новая может вернуть ошибку или прервать предыдущую ([README](https://github.com/nebius/api/blob/main/README.md)). Ошибки несут типизированные `ServiceError` (`QuotaFailure`, `OperationConflict`, `NotEnoughResources` и др.) с полем `retry_type` ([error.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/error.proto)):
- `CALL` — просто повторить вызов;
- `UNIT_OF_WORK` — повторить весь цикл read-modify-write;
- `NOTHING` — не повторять.

Идемпотентность обеспечивает заголовок `X-Idempotency-Key` с UUID ([README](https://github.com/nebius/api/blob/main/README.md); [gosdk conn/idempotency.go](https://github.com/nebius/gosdk/blob/main/conn/idempotency.go)). `Update` — это **полная замена, а не patch**. Сервер меняет поля, отличные от значений по умолчанию, плюс перечисленные в заголовке `X-ResetMask`, а списки и карты заменяет целиком. SDK управляют маской автоматически ([README](https://github.com/nebius/api/blob/main/README.md)). От гонок защищает `resource_version`, который принимается как ноль или текущее значение. gosdk `Operation.Wait` опрашивает операцию раз в секунду, а при сбоях отступает с экспоненциальной задержкой ([metadata.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/metadata.proto); [gosdk operations/operation.go](https://github.com/nebius/gosdk/blob/main/operations/operation.go)).

С сентября 2026 г. есть ещё `NodeGroupService.PreflightCheck`. Он принимает контекст вызова (`tool = "terraform"`, пути, неизвестные на этапе plan) и возвращает диагностику, `paths_require_recreate` и `requires_user_approval`. По сути, это серверная проверка плана ([preflight_check.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/preflight_check.proto); [commit 1396e2a](https://github.com/nebius/api/commit/1396e2a)). Слабые места тоже есть: `Delete` не принимает `resource_version`, каскадность удаления кластера вместе с группами не описана, а `List` фильтрует только по `parent_id` с пагинацией.

CKS отвечает на `Create`, `Update` и `Delete` **сразу объектом `Cluster`**, а клиент опрашивает `GetCluster`, пока статус не станет финальным. Официальный провайдер ждёт так ([resource_cluster.go](https://github.com/coreweave/terraform-provider-coreweave/blob/main/coreweave/cks/resource_cluster.go)):
- создание (`CREATING → RUNNING`) — до **45 минут**;
- обновление (`UPDATING`/`UPGRADING`) — до 20 минут;
- удаление — до 20 минут, причём `NotFound` считается успешным удалением.

При `STATUS_FAILED` провайдер требует удалить и пересоздать кластер.

Обновление делается через `PATCH` с `google.protobuf.FieldMask`, и в прото сказано «if not set, all fields will be updated». Однако сервер **проверяет наличие полей в сыром запросе, игнорируя маску**: например, отклоняет непустой `network` при обновлении версии. Поэтому с v0.13.0 (2 апреля 2026 г.) провайдер отправляет только изменённые поля, а маску передаёт «для прямой совместимости» ([resource_cluster.go](https://github.com/coreweave/terraform-provider-coreweave/blob/main/coreweave/cks/resource_cluster.go); [CHANGELOG](https://github.com/coreweave/terraform-provider-coreweave/blob/main/CHANGELOG.md)). Поле `version` в `UpdateClusterRequest` помечено как REQUIRED даже для обновлений, не связанных с версией. В v1beta1 нет ни ключа идемпотентности (в v1beta2 появляются `request_id` и `allow_missing`), ни etag или версии ресурса. Значит, конкурентные обновления работают по принципу last-writer-wins.

`ListClusters` не умеет пагинацию. Зато в 2026 г. он получил `return_partial_success` и список `unreachable` для «глобального endpoint», что указывает на веерный опрос зональных бэкендов ([buf.build/coreweave/cks](https://buf.build/coreweave/cks)). Ошибки — стандартные коды Connect с `google.rpc.errdetails` (`BadRequest.FieldViolations`, `QuotaFailure`, `PreconditionFailure`). Провайдер повторяет ответы 429 и 5xx до 10 раз с паузами от 200 мс до 5 с ([provider client.go](https://github.com/coreweave/terraform-provider-coreweave/blob/main/coreweave/client.go); [retry.go](https://github.com/coreweave/terraform-provider-coreweave/blob/main/coreweave/retry.go)).

Версии Kubernetes провайдеры тоже обновляют по-разному. У Nebius обновление минора идёт в два шага ([node_group_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group_service.proto)):
1. `ClusterService.Update` с новым `spec.control_plane.version`.
2. `NodeGroupService.Update` с `spec.version` для каждой группы.

Отдельный вызов `Upgrade(latest_infra_version)` двигает только патч и инфраструктурную версию внутри минора. `ListControlPlaneVersions` отдаёт список версий с флагами `deprecated` и `end_of_life`, а `GetCompatibilityMatrix` — совместимость ОС, пресета драйверов и платформы ([cluster_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster_service.proto)).

У CoreWeave обновление — это `PATCH` поля `version` на следующий минор при `is_upgradeable = true`. Шаг строго один минор, даунгрейда нет. Обновление блокируется, если кластер использует устаревшие API, нездоров или его узлы отстают больше чем на две версии. После обновления узлы перезагружают по пулам ([Upgrade Kubernetes](https://docs.coreweave.com/products/cks/clusters/upgrade-kubernetes)). RPC для списка версий в v1beta1 нет. Документация называет флаг `isUpgradable`, а JSON-имя в прото — `isUpgradeable` **[расхождение]**.

### Аутентификация: IAM-токены и сервисные аккаунты против пользовательских `CW-SECRET`

Nebius требует в каждом вызове заголовок `Authorization: Bearer <IAM access token>`. Пользователь получает токен через `nebius iam get-access-token`. Сервисный аккаунт подписывает JWT (RS256) своим ключом и обменивает его по RFC 8693 на `tokens.iam.api.nebius.cloud:443` или `https://auth.eu.nebius.com:443/oauth2/token/exchange` ([README](https://github.com/nebius/api/blob/main/README.md)). SDK обновляют токены сервисных аккаунтов в фоне, а пользовательские — нет ([gosdk README](https://github.com/nebius/gosdk/blob/main/README.md)).

Доступ к Kubernetes API тоже идёт через IAM. Команда `nebius mk8s cluster get-credentials --id … --external|--internal` пишет kubeconfig с exec-плагином `nebius mk8s v1 cluster get-token --format json` ([CLI get-credentials](https://docs.nebius.com/cli/reference/mk8s/cluster/get-credentials); [soperator terraform.tf](https://github.com/nebius/nebius-solution-library/blob/main/soperator/installations/example/terraform.tf)). Статических сертификатов и токенов нет, OIDC API-сервера настроить нельзя, а на клиенте нужен CLI `nebius`. Роли назначаются группам (auditor, viewer, editor, admin) ([Roles](https://docs.nebius.com/iam/authorization/roles)); как они отображаются на RBAC внутри кластера, проверить не удалось. Узлы получают идентичность через `service_account_id` шаблона. Каждый кластер публикует OIDC discovery и JWKS для workload identity ([OIDC public endpoint](https://docs.nebius.com/kubernetes/networking/oidc-public-endpoint)).

CoreWeave использует Bearer-токен вида **`CW-SECRET-<secret>`**. Токен создаётся в консоли, **привязан к пользователю** и наследует его IAM-роли: CKS Admin (создание, изменение и удаление кластеров и VPC) или CKS Viewer ([API access tokens](https://docs.coreweave.com/products/cks/auth-access/managed-auth/api-access); [CKS auth introduction](https://docs.coreweave.com/products/cks/auth-access/introduction)). Сервисных аккаунтов в привычном смысле найти не удалось. Машинный доступ с короткоживущими учётными данными появился только в провайдере v0.23.0 (3 сентября 2026 г.): туда добавлены обновляемый источник токена и OIDC-конфигурации Workload Federation для внешних IdP вроде GitHub Actions ([CHANGELOG](https://github.com/coreweave/terraform-provider-coreweave/blob/main/CHANGELOG.md)).

Зато над аутентификацией самого API-сервера CoreWeave даёт больше контроля ([buf.build/coreweave/cks](https://buf.build/coreweave/cks); [cwic README](https://github.com/coreweave/cwic/blob/main/README.md); [Tailscale VPN](https://docs.coreweave.com/products/cks/auth-access/tailscale-vpn)):
- Managed Auth — токен CoreWeave в kubeconfig или exec-плагин `cwic auth k8s api-token`;
- Unmanaged Auth — поля `oidc` (в v1beta1 только RS256), `authn_webhook`/`authz_webhook` и `admin_group_binding` для привязки группы к `cluster-admin`;
- доступ через Tailscale.

`cwic cluster auth` генерирует kubeconfig только для кластеров с публичным endpoint, а отдельного RPC вроде «GetKubeconfig» нет. Издатель токенов сервисных аккаунтов Kubernetes публикуется на `oidc.cks.coreweave.com/id/<cluster-id>`, если не включён `disable_oidc_info_publication`.

### Инструменты: полный набор SDK против Terraform без пулов узлов

Nebius поставляет Go SDK `github.com/nebius/gosdk` и Python SDK (`pip install nebius`). Оба генерируются из `nebius/api` и публикуются в один день с протобуфами: последний релиз gosdk от 13 октября 2026 г. собран из того же исходного хеша ([gosdk](https://github.com/nebius/gosdk); [pysdk README](https://github.com/nebius/pysdk/blob/main/README.md)). К SDK добавляются CLI `nebius mk8s cluster|node-group …` и Terraform-провайдер `nebius/nebius` с ресурсами `nebius_mk8s_v1_cluster` и `nebius_mk8s_v1_node_group`. В провайдере metadata «расплющена» в атрибуты `parent_id`, `name` и `labels` ([k8s-training/main.tf](https://github.com/nebius/nebius-solution-library/blob/main/k8s-training/main.tf)). Версию провайдера проверить не удалось: реестр был заблокирован. Квоты по умолчанию — **2 кластера на регион**. Число узлов ограничено квотой «Total number of allocations» (1000, около 498 узлов с приватными IP без HA), и в группе не больше 100 узлов ([quotas & limits](https://docs.nebius.com/kubernetes/resources/quotas-limits)).

У CoreWeave CKS API и Terraform-провайдер вышли 21 февраля 2025 г. ([CKS API and Terraform release note](https://docs.coreweave.com/release-notes/cks-api-terraform)). Текущий провайдер `coreweave/coreweave` v0.24.0 (11 сентября 2026 г.) содержит ресурсы `coreweave_cks_cluster` и `coreweave_networking_vpc` и data source `coreweave_caller_identity`, но **ресурса пула узлов в нём нет** ([provider docs/resources](https://github.com/coreweave/terraform-provider-coreweave/tree/main/docs/resources); [CHANGELOG](https://github.com/coreweave/terraform-provider-coreweave/blob/main/CHANGELOG.md)). CLI называется cwic и помечен как «in-development … as-is» ([cwic README](https://github.com/coreweave/cwic/blob/main/README.md)). Написанных вручную SDK нет, есть только сгенерированные Buf клиенты вроде `buf.build/gen/go/coreweave/cks/...`. Пакет Pulumi `coreweave` с ресурсом `CksCluster`, судя по всему, — мост от Terraform-провайдера **[не проверено]** ([Pulumi registry](https://www.pulumi.com/registry/packages/coreweave/api-docs/ckscluster/)). К тому же провайдер закреплён на ревизии модуля cks от 26 марта 2026 г., хотя последняя опубликована 30 июня ([provider go.mod](https://github.com/coreweave/terraform-provider-coreweave/blob/main/go.mod)).

### Сводная таблица API

| Аспект | Nebius mk8s | CoreWeave CKS |
|---|---|---|
| Транспорт | Только gRPC/protobuf, без `google.api.http` | Connect RPC, gRPC и REST/JSON (транскодирование `google.api.http`) |
| Endpoint | `mk8s.api.nebius.cloud:443`, без региона в имени | `https://api.coreweave.com`; глобальный endpoint опрашивает зональные бэкенды (вывод) |
| Версия | `nebius.mk8s.v1` (де-факто GA); `v1alpha1` deprecated | `coreweave.cks.v1beta1`; `v1beta2` опубликован, но не документирован |
| Публикация схем | `github.com/nebius/api` | `buf.build/coreweave/cks`, `buf.build/coreweave/networking` |
| Иерархия | tenant → project → cluster → node group (`parent_id`) | Нет: организация берётся из токена, расположение — `zone` |
| Идентификаторы | Типизированные NID `<type>-<routing><id>` | Серверный `id`; в v1beta2 — имя-UUID |
| Имя кластера | 1–63 символа: буквы, цифры, `-`, `_` | 1–30 символов, DNS-метка в нижнем регистре |
| Объекты облачного API | `Cluster`, `NodeGroup`, `Operation` | `Cluster`, `VPC` |
| Пулы узлов | `NodeGroupService` в том же API | CRD `NodePool` `compute.coreweave.com/v1alpha1` внутри кластера |
| Размер пула | `fixed_node_count` или `autoscaling{min,max}`, 0–100 | `targetNodes`/`targetRacks`, `minNodes`/`maxNodes`; молча заполняется до квоты |
| Ответ на мутацию | `Operation` (LRO); завершена, когда задан `status` | Сразу `Cluster`; опрос `GetCluster` до финального статуса |
| Семантика Update | Полная замена + `X-ResetMask`; `resource_version` | `PATCH` + `FieldMask`, на практике — только изменённые поля; etag нет |
| Идемпотентность | `X-Idempotency-Key` | В v1beta1 нет; `request_id` в v1beta2 |
| Конкурентность | Одна операция на ресурс; оптимистическая блокировка | Last-writer-wins |
| Ошибки | `ServiceError` с `retry_type` | Коды Connect + `google.rpc.errdetails` |
| Состояния | `PROVISIONING`/`RUNNING`/`DELETING`, плюс `events` и `reconciling` | 10 статусов, включая `ERROR`, `FAILED`, `UPGRADE_FAILED`, `PREEMPTED` |
| List | `page_size`/`page_token`, без фильтров | Без пагинации; `return_partial_success`; CEL-`filter` в v1beta2 |
| Версии K8s через API | `ListControlPlaneVersions`, `GetCompatibilityMatrix` | RPC нет; только флаг `is_upgradeable` |
| Проверка плана | `PreflightCheck` (с сентября 2026 г.) | Нет |
| Сеть кластера | `subnet_id` + один `service_cidrs`; pod CIDR нет; только IPv4 | VPC с именованными префиксами pod/service/LB; dual-stack |
| Доступ к API-серверу | Публичный endpoint опционален, до 8 IPv4-CIDR; приватный есть всегда | Флаг `public`, Tailscale, `additional_server_sans` |
| Аутентификация в облаке | IAM-токен; сервисные аккаунты с обменом JWT | Пользовательский `CW-SECRET-…`; Workload Federation с сентября 2026 г. |
| Аутентификация в K8s | Exec-плагин `nebius` (IAM) | Managed (токен или cwic exec) либо OIDC/вебхуки |
| Раскатка узлов | `strategy`: `max_unavailable`, `max_surge`, `drain_timeout` | `nodeConfigurationUpdateStrategy`, `scaleDownStrategy` |
| Ёмкость и цена в API | `reservation_policy` (AUTO/FORBID/STRICT), `pricing_model`, `preemptible` | `computeClass` (`default`/spot); резервации — это биллинг, а не поле |
| SDK | gosdk, pysdk (`nebius`) | Только сгенерированные Buf клиенты |
| CLI | `nebius` | `cwic` (в разработке) |
| Terraform | `nebius/nebius`: кластер и группа узлов | `coreweave/coreweave`: кластер и VPC; пулы — через `kubernetes_manifest` |

## Продукт: бесплатный HA на VM против bare metal с Mission Control

### Control plane: SLA и HA формально описаны только у Nebius

Оба провайдера отдают control plane бесплатно. В прайсе Nebius значится «Managed Service for Kubernetes: Free» ([Nebius prices](https://nebius.com/prices)). У CoreWeave «Free» указано для control plane CKS, а «Free*» — для SUNK без учёта CPU, на котором он работает ([CoreWeave pricing](https://www.coreweave.com/pricing)). Nebius по умолчанию создаёт **три etcd-узла** и подчёркивает, что HA «does not affect the cost of the cluster» ([Components](https://docs.nebius.com/kubernetes/components)). SLA обещает **99,9%** доступности control plane, но только для HA-конфигурации (etcd 3–5) и только при недоступности дольше 5 минут. Кредит составляет 10% ниже 99,9% и 25% ниже 95% ([SLA](https://docs.nebius.com/legal/sla-levels/managed-kubernetes)). Не-HA control plane во время обновления недоступен ([manage versions](https://docs.nebius.com/kubernetes/manage-versions)).

CoreWeave с 7 июля 2025 г. вынес управляемые компоненты «out-of-band» и убрал у новых кластеров пул `cpu-control-plane` ([Control Plane Node Pools](https://docs.coreweave.com/docs/changelog/release-notes/control-plane-node-pools)). Компания прямо пишет, что «owns the API server, etcd, the scheduler, and the controllers» ([What CoreWeave manages](https://docs.coreweave.com/support/what-coreweave-manages)). Однако ни описания топологии HA, ни SLA на control plane CKS найти не удалось. Официальный SLA есть только у AI Object Storage (99,9%), а цифры 99,9%/99% по compute встречаются лишь в стороннем обзоре **[не проверено]** ([AI Object Storage SLA](https://docs.coreweave.com/policies/terms-of-service/coreweave-ai-object-storage-policy); [whtop](https://www.whtop.com/review/coreweave.com)).

С версиями ясности у обоих меньше, чем хотелось бы. Документация Nebius перечисляет 1.33, 1.34 и 1.35, по умолчанию 1.35 ([Kubernetes versions](https://docs.nebius.com/kubernetes/versions)). Релиз Aether 3.6 в июне 2026 г. сообщал об обновлении до 1.34 ([Aether 3.6](https://nebius.com/blog/posts/ai-cloud-aether-3-6)). А 11 сентября 2026 г. CNCF приняла результаты проверки конформности «v1.36/nebius» ([cncf/k8s-conformance PR #4392](https://github.com/cncf/k8s-conformance/pull/4392)). CKS поддерживает три последних минора, но источники противоречат друг другу **[расхождение]** ([Cluster components](https://docs.coreweave.com/products/cks/reference/cluster-components); [Changelog](https://docs.coreweave.com/changelog)):
- одно извлечение со страницы компонентов даёт v1.34–v1.36 с версией по умолчанию 1.36;
- другое извлечение той же страницы даёт v1.35–v1.37 с 1.37 по умолчанию;
- changelog называет версией по умолчанию 1.35 или 1.36;
- пример Terraform использует v1.35.

Обновления у обоих инициирует клиент, и проходят они в два шага. Nebius обновляет сначала control plane, затем группы узлов, причём группа не может обогнать control plane ([manage versions](https://docs.nebius.com/kubernetes/manage-versions)). Документация при этом утверждает, что автообновления нет. Прото же говорит, что желаемая версия «may be lower than the actual cluster version if … the cluster has been automatically updated», и описывает `end_of_life` как момент, когда версия «will be forced out of use» **[расхождение]** ([cluster.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto); [cluster_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster_service.proto)). На практике это означает принудительное обновление по EOL. CoreWeave обновляет строго по одному минору. Узлы сами не обновляются и требуют перезагрузки, а начиная с 1.35 им нужен cgroup v2 ([Upgrade Kubernetes](https://docs.coreweave.com/products/cks/clusters/upgrade-kubernetes)).

Доступ к API-серверу у Nebius гибче. Публичный endpoint включён по умолчанию, и с июня 2026 г. его можно без простоя ограничить списком IPv4-CIDR, который применяется через security groups. Приватный endpoint всегда доступен из VPC ([Access restriction](https://docs.nebius.com/kubernetes/networking/limit-access-to-public-endpoint); [Aether 3.6](https://nebius.com/blog/posts/ai-cloud-aether-3-6)).

У CoreWeave кластеры, созданные в режиме самообслуживания, публичны по умолчанию. Приватный кластер документация предлагает заказывать через поддержку, хотя в API есть поле `public`, а пример Terraform ставит `public = false` **[расхождение]** ([CKS auth introduction](https://docs.coreweave.com/products/cks/auth-access/introduction); [TF cks_cluster docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/resources/cks_cluster.md)). Приватный доступ строится через прокси Tailscale, Traefik или Direct Connect ([Tailscale VPN](https://docs.coreweave.com/products/cks/auth-access/tailscale-vpn)). Allowlist по IP для публичного endpoint найти не удалось.

Аудит-логи Nebius включаются явно (`--control-plane-audit-logs`), а логи control plane пишутся всегда. Аудит запрашивают через LogCLI по метке `sp_mk8s_audit_logs` ([Logs in Managed Kubernetes](https://docs.nebius.com/kubernetes/logs)). CoreWeave даёт настраиваемый `audit_policy` в API и пересылку аудита через Telemetry Relay ([CKS introduction](https://docs.coreweave.com/products/cks)). С 26 марта 2026 г. доступ поддержки CoreWeave к окружению клиента требует его одобрения ([Support Access Management](https://docs.coreweave.com/release-notes/support-access-management)).

### Вычисления: VM против bare metal, но стойки NVL72 нарезаны одинаково

Фундаментальное различие — подложка. «CKS runs Kubernetes directly on bare metal Nodes, without a hypervisor» ([CKS introduction](https://docs.coreweave.com/products/cks)). Узлы mk8s, напротив, — это VM Nebius Compute: GPU-автовосстановление останавливает и запускает VM, чтобы перенести узел на исправное железо ([Health checks](https://docs.nebius.com/kubernetes/maintenance/health-checks)). Варианта bare metal в mk8s не найдено.

Каталоги GPU пересекаются, но не совпадают. У Nebius в группах узлов доступны ([VM & GPU types](https://docs.nebius.com/compute/virtual-machines/types); [Regions](https://docs.nebius.com/overview/regions); [NCCL tests](https://docs.nebius.com/kubernetes/gpu/nccl-test); [InfiniBand GPU clusters](https://docs.nebius.com/kubernetes/gpu/clusters)):
- `gpu-h100-sxm` — только в eu-north1;
- `gpu-h200-sxm` — в eu-north1, eu-north2, eu-west1 и us-central1;
- L40S и `gpu-rtx6000`;
- `gpu-b200-sxm` с пресетом `8gpu-160vcpu-1792gb` в us-central1;
- `gpu-b300-sxm` с пресетом `8gpu-192vcpu-2768gb` в uk-south1, eu-west2 и us-north1.

GB300 NVL72 реализован как `gpu-gb300` с пресетом `4gpu-112vcpu-800gb`. Ограничения такие ([k8s-training README](https://github.com/nebius/nebius-solutions-library/blob/main/k8s-training/README.md)):
- **18 узлов на стойку**;
- на каждую стойку — одна группа узлов и одна NVLink instance group;
- для двух и более стоек — общая XDR InfiniBand-фабрика;
- без автомасштабирования, preemptible-узлов и MIG.

GB200 Nebius объявила общедоступным в Европе 11 июня 2025 г. ([Nebius newsroom](https://nebius.com/newsroom/nebius-delivers-first-nvidia-blackwell-general-availability-in-europe-brings-nvidia-ai-enterprise-to-nebius-ai-cloud)), но пресета для mk8s найти не удалось.

В каталоге CoreWeave — Vera Rubin NVL72 (limited availability с 30 сентября 2026 г.), GB300 NVL72 в вариантах Quantum-X InfiniBand и Spectrum-X RoCE, GB200 NVL72, B300, B200, H200, H100 и RTX PRO 6000 ([About GPU instances](https://docs.coreweave.com/platform/instances/gpu-instances); [Cognition blog](https://www.coreweave.com/blog/cognition-becomes-first-customer-for-nvidia-vera-rubin-nvl72-on-coreweave-cloud)). Узел GB300 NVL72 у CoreWeave — это 4 GPU по 279 ГБ, 144 vCPU и 960 ГБ RAM; стойка тоже состоит из 18 узлов ([GB300 NVL72](https://docs.coreweave.com/platform/instances/gpu/gb300-4x); [Topology and block scheduling](https://docs.coreweave.com/products/sunk/optimize_workloads/topology-scheduling)). GB300 запущен 19 августа 2025 г. ([GB300 release note](https://docs.coreweave.com/release-notes/gb300-nvl72)). На одном и том же железе пресет Nebius даёт 112 vCPU и 800 ГБ против 144 vCPU и 960 ГБ у CoreWeave. Правдоподобно, что разницу съедает гипервизор, но это **[гипотеза]**.

Модели ёмкости тоже различаются. Nebius предлагает on-demand, preemptible-узлы и резервирование через capacity block groups. Такие группы создаёт менеджер Nebius под договор со скидкой за обязательства. Резервации доступны только обычным GPU-группам и умеют откатываться на PAYG, когда резерв исчерпан ([Reservations for node groups](https://docs.nebius.com/kubernetes/node-groups/reservations); [Capacity block groups](https://docs.nebius.com/overview/limits/capacity-block-groups)). В API это поле `reservation_policy` со значениями `AUTO`, `FORBID` и `STRICT` ([node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)).

С октября 2026 г. цены на preemptible-узлы, по данным btw.media, стали динамическими ([btw.media](https://btw.media/en/nebius-gpu-spot-price-cost-cap-scheduling-rule)):
- меняются раз в 15 минут;
- не превышают on-demand минус один цент;
- начинаются от $0,79 за GPU-час для H100/H200/RTX PRO 6000 и от $0,99 для B200/B300.

Прайс Nebius при этом всё ещё показывает фиксированные $0,95 для RTX PRO 6000 **[расхождение]** ([Compute pricing](https://docs.nebius.com/compute/resources/pricing)).

CoreWeave продаёт Reservations, Flex Reservations, On-Demand и Spot; потребление списывается в порядке «резерв → Flex → on-demand» ([About capacity plans](https://docs.coreweave.com/platform/capacity-plans)). Spot Node Pools появились 10 марта 2026 г. Железо в них то же самое. При изъятии ёмкости узел сразу кордонится, а задачам даётся до **7 минут** на остановку ([Spot release note](https://docs.coreweave.com/release-notes/spot-node-pools); [Spot Node Pools](https://docs.coreweave.com/platform/capacity-plans/spot-node-pools)). Квоты считаются в разрезе «организация × тип инстанса × compute class × зона» ([Resource quotas](https://docs.coreweave.com/products/cks/clusters/quotas)), а доступность ёмкости проверяет Capacity Finder API ([Capacity Finder](https://docs.coreweave.com/platform/capacity-plans/capacity-finder-api/capacityservice/get-capacity-availability)).

### GPU-стек: свобода драйверов у Nebius, управляемый оператор у CoreWeave

Nebius оставляет выбор клиенту. Пресет драйверов (например, `cuda13.0`) выбирает образ Ubuntu 24.04 с предустановленными драйверами. Без пресета драйверы ставят сами, а смена пресета пересоздаёт все узлы группы ([Working with GPUs](https://docs.nebius.com/kubernetes/gpu/set-up)). Для собственных образов нужен NVIDIA GPU Operator, а в ряде случаев, например с B200, — ещё и Network Operator. Оба оператора есть в каталоге приложений ([Working with GPUs](https://docs.nebius.com/kubernetes/gpu/set-up); [nebius-k8s-applications](https://github.com/nebius/nebius-k8s-applications)). С 14 сентября 2026 г. в API есть DRA: он отключает legacy device plugin, а для узлов в GPU-кластере объявляет RDMA-возможности через управляемый DaemonSet DRANet ([node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)).

InfiniBand подключается через GPU cluster и только для 8-GPU узлов. Топология IB публикуется метками узлов, но их имена извлечь не удалось. Есть руководство по NCCL-тестам ([InfiniBand GPU clusters](https://docs.nebius.com/kubernetes/gpu/clusters); [NCCL tests](https://docs.nebius.com/kubernetes/gpu/nccl-test)). MIG настраивается параметрами GPU Operator (`mig_strategy`, `mig_parted_config`) в Terraform-решении. Конфигурацией IMEX для GB300 владеет инфраструктура mk8s ([k8s-training README](https://github.com/nebius/nebius-solutions-library/blob/main/k8s-training/README.md)).

CoreWeave, наоборот, поставляет готовый стек и не даёт его менять. Драйверы предустановлены, а **NVIDIA GPU Operator управляется платформой**: документация предупреждает, что ставить его самостоятельно нельзя ([Introduction to clusters](https://docs.coreweave.com/products/cks/clusters/introduction)). DCGM стоит на всех серверах, вторичный InfiniBand-интерфейс подключает Multus, а MetalLB на новых кластерах заменён Cilium LB IPAM ([Cluster components](https://docs.coreweave.com/products/cks/reference/cluster-components)).

Сильная сторона CoreWeave — таксономия топологических меток ([NVL72 instances](https://docs.coreweave.com/platform/instances/nvl72); [InfiniBand labels](https://docs.coreweave.com/docs/products/networking/hpc-interconnect/infiniband-labels)):
- `ds.coreweave.com/nvlink.domain` — на NVL72 это ID стойки;
- `node.coreweave.cloud/rack`;
- `ib.coreweave.cloud/fabric`, `ib.coreweave.cloud/leafgroup` и `ib.coreweave.cloud/superpod`.

Эти метки напрямую потребляет Kueue из чарта `coreweave/cks-kueue`, где TopologyAwareScheduling включён по умолчанию ([Kueue](https://docs.coreweave.com/products/cks/clusters/coreweave-charts/kueue)). MIG не документирован; сторонний обзор отмечает, что клиенты обычно берут машины целиком ([clustermax.ai](https://www.clustermax.ai/cloudreview/coreweave)). ClusterMAX 2.0 критиковал отключённый доступ host-to-BMC ([ClusterMAX 2.0](https://newsletter.semianalysis.com/p/clustermax-20-the-industry-standard)).

### Надёжность: пассивные проверки раз в 5 минут против ежечасной HPC Verification

В mk8s на каждом узле работает Node Problem Detector. Платформа реагирует так ([Health checks](https://docs.nebius.com/kubernetes/maintenance/health-checks)):
- ошибка ввода-вывода загрузочного диска дольше 30 секунд выставляет условие `NebiusBootDiskIOError`, и узел заменяется;
- если условие Ready находится в False дольше 5 минут или в Unknown дольше 15 минут, узел удаляется и создаётся новый с другим именем;
- GPU, InfiniBand и NVLink проверяются **каждые 5 минут** через nvidia-smi, dcgmi и dmesg (Xid, ECC);
- при `NebiusGPUError = True` узел кордонится, ждёт завершения GPU-нагрузок, дренируется до часа и переезжает на исправное железо через остановку и запуск VM.

Ремонт можно отключить для отдельных условий через `auto_repair.conditions` ([Enable/disable health checks](https://docs.nebius.com/kubernetes/maintenance/enable-disable-health-checks)). Так и делает официальный Terraform Soperator, чтобы избежать гонок со Slurm ([k8s_ng_workers_v2.tf](https://github.com/nebius/nebius-solution-library/blob/main/soperator/modules/k8s/k8s_ng_workers_v2.tf)). Активные (нагрузочные) проверки включены по умолчанию в Soperator, а для mk8s доступны **только в preview по запросу** ([How we build reliable clusters](https://nebius.com/blog/posts/how-we-build-reliable-clusters)).

CoreWeave делает надёжность главным продуктом. Node Lifecycle Controller в Mission Control сочетает пассивный мониторинг in-band и out-of-band телеметрии и логов с **HPC Verification**. Эта проверка раз в час на 20–30 минут нагружает все GPU простаивающих узлов ([Day 2+](https://docs.coreweave.com/platform/fleet-management/node-lifecycle/day2)). SemiAnalysis оценивает частоту пассивных проверок как «every couple of seconds» ([ClusterMAX rating system](https://newsletter.semianalysis.com/p/the-gpu-cloud-clustermax-rating-system-how-to-rent-gpus)). Исходов два ([Node lifecycle](https://docs.coreweave.com/platform/fleet-management/node-lifecycle)):
- «solved by reboot» — узел перезагружается и возвращается в кластер;
- «needs triage» — узел уходит на диагностику и RMA, а CKS автоматически поставляет замену.

Переходы между состояниями (`production`, `production-reboot`, `production-powercycle` и другими) ждут, пока на узле не останется активных подов ([Node state transitions](https://docs.coreweave.com/platform/fleet-management/node-lifecycle/state-transitions)). Для этого есть условие `CWActive`, а также безопасная и принудительная перезагрузка ([Reboot Nodes](https://docs.coreweave.com/products/cks/nodes/reboot)). Опция `prefill` заранее поднимает узел на замену, но работает только без автомасштабирования и не на Spot ([Node Pool reference](https://docs.coreweave.com/products/cks/reference/node-pool)). Обратная сторона автоматики: она может снять обычный `kubectl cordon`, поэтому для долгой изоляции нужен собственный taint ([Node cordoning](https://docs.coreweave.com/products/cks/nodes/cordon)). Инфраструктурные алерты попадают в графики запусков W&B (preview) ([W&B infrastructure alerts](https://docs.wandb.ai/models/runs/infrastructure-alerts)). Сама CoreWeave заявляет до 96% goodput ([SUNK expands capabilities](https://investors.coreweave.com/news/news-details/2026/CoreWeave-SUNK-Expands-Capabilities-to-Bring-AI-Workloads-Online-Faster--Anywhere/default.aspx)).

Независимая оценка выровняла поле. В ClusterMAX 3.0 (23 сентября 2026 г.) CoreWeave получила Platinum в третий раз подряд, а Nebius поднялась с Gold до Platinum ([CoreWeave blog](https://www.coreweave.com/blog/coreweave-becomes-the-only-provider-to-earn-three-consecutive-platinum-clustermax-tm-ratings); [Nebius blog](https://nebius.com/blog/posts/nebius-platinum-clustermax-3-0)). SemiAnalysis называет активные проверки CoreWeave «unusually robust», но оговаривает, что это «certainly not the only provider with active health checks» ([ClusterMAX 3.0](https://newsletter.semianalysis.com/p/clustermax-30-the-industry-standard)).

### Автомасштабирование: один Cluster Autoscaler, одинаковые исключения

Оба провайдера масштабируют узлы upstream Cluster Autoscaler'ом поверх заранее описанных групп. У Nebius это min/max на группу, до 100 узлов. По документации, для существующих групп автомасштабирование настраивается только через CLI. Чтобы GPU-группа могла сжиматься, CoreDNS и Cilium рекомендуют держать на отдельной CPU-группе ([Autoscaling](https://docs.nebius.com/kubernetes/node-groups/autoscaling)). Валидация API допускает `min_node_count = 0`, но масштабирование с нуля документально не подтверждено.

Важная находка касается Karpenter. Исследование документации Nebius его не обнаружило, однако в прото `ControlPlaneSpec` с 16 марта 2026 г. есть поле `karpenter` **[расхождение]** ([cluster.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto); [commit 4bcb49f](https://github.com/nebius/api/commit/4bcb49f)). По описанию в прото, это Helm-чарт внутри кластера, который требует хотя бы одной публичной CPU-группы и не имеет «feature parity» с обычными группами.

У CoreWeave автомасштабирование пула задаётся через `minNodes`/`maxNodes` и требует Kubernetes 1.32+. Сжатие опирается на утилизацию, а не на `CWActive`, поэтому без защитной аннотации может удалить активный узел ([Autoscale Node Pools](https://docs.coreweave.com/products/cks/nodes/autoscaling)). Ни у одного провайдера нельзя автомасштабировать стойки GB200/GB300 NVL72. У CoreWeave то же касается пулов SUNK, а scale-down не согласуется со Slurm ([SUNK Pod Scheduler](https://docs.coreweave.com/products/sunk/run_workloads/manage-scheduler-resources)). Spot-пулы CoreWeave переживают полное вытеснение, оставаясь с нулём узлов, и сами восстанавливаются ([Spot Node Pools](https://docs.coreweave.com/platform/capacity-plans/spot-node-pools)). Рост любых пулов ограничен квотой.

### Хранилище и сеть: Cilium у обоих, данные идут разными путями

В сети провайдеры похожи больше всего. В mk8s по умолчанию стоят Cilium и CoreDNS, а kube-proxy всё ещё значится среди компонентов узла ([Networking add-ons](https://docs.nebius.com/kubernetes/networking/add-ons); [Components](https://docs.nebius.com/kubernetes/components)). Балансировщики по умолчанию получают публичный IP; внутренний задаётся аннотацией `nebius.com/load-balancer-type: internal`. Есть и аннотации для переиспользования аллокаций и выбора пула IP ([Load balancers](https://docs.nebius.com/kubernetes/clusters/load-balancer)).

CKS работает на Cilium v1.18.1; Calico для новых кластеров не поддерживается. Адреса балансировщиков, публичные и приватные, раздаёт Cilium LB IPAM. Исходящий трафик идёт через NAT с опубликованными диапазонами IP по зонам, а DNS обслуживает node-local-dns ([Cluster components](https://docs.coreweave.com/products/cks/reference/cluster-components); [About networking](https://docs.coreweave.com/products/networking)). Цена управляемости — ограничения: сетевые политики CKS обязаны разрешать `100.124.0.0/18`, а FQDN-политики для egress недоступны, потому что конфигурацией Cilium управляет CoreWeave ([Cilium network policy on CKS](https://docs.coreweave.com/products/networking/cilium-network-policy-cks-patterns)).

С данными подходы расходятся. Nebius монтирует Shared Filesystem на узлы через virtiofs в cloud-init и отдаёт её подам через CSI, но с ограничениями ([Shared filesystems over CSI](https://docs.nebius.com/kubernetes/storage/filesystem-over-csi); [k8s-training README](https://github.com/nebius/nebius-solutions-library/blob/main/k8s-training/README.md)):
- ФС должна быть смонтирована во **все** группы узлов;
- режим Block не поддерживается;
- размер через API не меняется.

Сам прото называет монтирование ФС «workaround, until CSI for Compute Disk and Filesystem will be available» ([node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)). Object Storage подключается через Mountpoint for S3 CSI, причём режим RWX включают через поддержку ([Mountpoint for S3](https://docs.nebius.com/object-storage/interfaces/mountpoint-s3)). ClusterMAX 3.0 нашёл на одном из уровней хранилища Nebius ошибки EEXIST при параллельном создании каталогов со 128 и более рангов; Nebius перенастроила ФС прямо во время ревью ([Nebius blog](https://nebius.com/blog/posts/nebius-platinum-clustermax-3-0)).

CoreWeave делает ставку на объектное хранилище. AI Object Storage автоматически раскладывает данные по уровням Hot/Warm/Cold и работает **без платы за egress, ingress и запросы** ([Storage for AI](https://www.coreweave.com/products/storage)). Его ускоряет прокси LOTA на каждом узле с кешем 1 ТиБ ([LOTA](https://docs.coreweave.com/products/storage/object-storage/improving-performance/about-lota)); вендор заявляет до 7 ГБ/с на GPU ([Forbes](https://www.forbes.com/sites/janakirammsv/2025/10/22/coreweave-empowers-ai-with-high-speed-flexible-object-storage/)). POSIX-доступ даёт Distributed File Storage на VAST через класс `shared-vast` с RWX ([Create DFS volumes](https://docs.coreweave.com/products/storage/distributed-file-storage/create-volumes)), а CSI-драйвер предустановлен ([CKS introduction](https://docs.coreweave.com/products/cks)).

### Экосистема и наблюдаемость: открытый Soperator против вертикально интегрированного стека CoreWeave

Slurm поверх Kubernetes есть у обоих, но модели разные. Soperator от Nebius — открытый оператор под Apache 2.0 с ресурсом `SlurmCluster`. Его возможности: общая корневая ФС («jail»), пассивные и активные проверки, эфемерные воркеры и учёт IB-топологии. Он требует Kubernetes 1.35+ и Ubuntu 24.04, работает и в других облаках и продаётся как Managed Soperator и Soperator Pro ([nebius/soperator](https://github.com/nebius/soperator); [Nebius Soperator](https://nebius.com/services/soperator)). Эфемерные узлы умеют возвращать ёмкость в общий capacity block, например для inference-кластера ([Ephemeral nodes](https://docs.nebius.com/slurm-soperator/capacity/ephemeral-nodes)). SUNK от CoreWeave в режиме Self-Service описывает Slurm-кластер одним Kubernetes-ресурсом, а control plane Slurm, login-поды и хранилище берёт на себя CoreWeave ([About SUNK Self-Service](https://docs.coreweave.com/products/sunk/about-self-service-sunk)). Его сильные стороны — блоки по 18 узлов NVL72 ([Topology](https://docs.coreweave.com/products/sunk/optimize_workloads/topology-scheduling)) и node locking, при котором простаивающие Slurm-узлы выполняют K8s-поды ([Node locking](https://docs.coreweave.com/products/sunk/discover_sunk/node-locking)).

Аддоны Nebius распространяет через каталог Applications примерно из 37 Helm-приложений ([nebius-k8s-applications](https://github.com/nebius/nebius-k8s-applications)). В нём есть GPU/Network Operator, Ray, vLLM, Kubeflow, Volcano, Run:ai, OSMO, dstack, Argo CD и «Custom Helm chart». Гарантии жизненного цикла этих приложений не описаны. CoreWeave предлагает проверенную коллекцию CoreWeave Charts, включая `cks-kueue` ([CoreWeave Charts](https://docs.coreweave.com/products/cks/clusters/coreweave-charts/introduction)), руководства по KubeRay с Kueue ([Ray with Kueue](https://docs.coreweave.com/products/cks/clusters/frameworks/ray-with-kueue)) и выпущенный 31 марта 2026 г. CoreWeave Inference ([Inference release](https://docs.coreweave.com/release-notes/inference)).

Для наблюдаемости Nebius ставит Observability Agent. Метрики отдаются через Prometheus-совместимый endpoint `https://read.monitoring.api.nebius.cloud/projects/<project_ID>/service-provider/prometheus`, а логи — через Loki-совместимый источник в Grafana ([Grafana with Nebius metrics](https://docs.nebius.com/observability/metrics/grafana); [Logging](https://docs.nebius.com/observability/logging)). CoreWeave даёт бесплатную Managed Grafana с готовыми read-only дашбордами и PromQL/LogQL API, но **без собственных алертов** ([CoreWeave Grafana](https://docs.coreweave.com/observability/managed-grafana); [Query logs and metrics](https://docs.coreweave.com/observability/logs-metrics/introduction)). Экспорт идёт через Telemetry Relay. После поглощения W&B 5 мая 2025 г. инфраструктурные события Mission Control привязываются к запускам обучения ([W&B is part of CoreWeave](https://www.coreweave.com/companies/weights-and-biases)).

### Регионы и цены: control plane бесплатен, счёт идёт за узлы

mk8s доступен во всех регионах Nebius ([Regions](https://docs.nebius.com/overview/regions); [changelog](https://docs.nebius.com/changelog)):
- eu-north1 (Финляндия);
- eu-north2;
- eu-west1;
- us-central1 (Канзас-Сити);
- uk-south1;
- частные регионы eu-west2 (Париж) и us-north1 (Вудбери, Миннесота).

Устаревшая страница nebius.ai указывает «8 clusters per cloud», но это прежняя платформа; для текущей действует квота в 2 кластера на регион ([quotas & limits](https://docs.nebius.com/kubernetes/resources/quotas-limits); [legacy limits](https://nebius.ai/docs/managed-kubernetes/concepts/limits)). CoreWeave группирует ёмкость в суперрегионы US-EAST, US-CENTRAL, US-WEST, EU-NORTH (Швеция, Норвегия, Дания), EU-SOUTH (Испания) и EU-WEST (Великобритания). Регионы делятся на General Access и Dedicated Access ([All Availability Zones](https://docs.coreweave.com/platform/regions/all-availability-zones)).

В обоих случаях платят за узлы. Nebius берёт за GPU-час — по тарифу on-demand, preemptible или со скидкой за обязательства. CoreWeave берёт за час инстанса: например, spot HGX B300 стоит $35,84/ч в Северной Америке, а GB300 NVL72 продаётся только через отдел продаж ([CoreWeave pricing](https://www.coreweave.com/pricing)). Если $35,84 — цена 8-GPU инстанса, это около $4,48 за GPU-час, но единица не подтверждена **[не проверено]**. Сторонний мониторинг зафиксировал в 2026 г. несколько инцидентов CKS **[не проверено]**: 26 августа — 22-минутный сбой создания и обновления кластеров через консоль и Terraform, 5 октября — двухчасовой сетевой инцидент в US-CENTRAL-09A ([IsDown: console](https://isdown.app/status/coreweave/incidents/644505-cloud-console-service-disruption); [IsDown: US-CENTRAL-09A](https://isdown.app/status/coreweave/incidents/665120-us-central-09a-networking-incident)).

### Сводная таблица функций

| Аспект | Nebius mk8s | CoreWeave CKS |
|---|---|---|
| Цена control plane | Бесплатно | Бесплатно (SUNK — «Free*», без учёта CPU) |
| HA control plane | 3 узла etcd по умолчанию, без доплаты; можно выбрать 1 | Работает out-of-band; топология не опубликована |
| SLA control plane | 99,9% (только для HA), кредиты 10% и 25% | Не найден; есть только SLA AI Object Storage 99,9% |
| Версии K8s | 1.33–1.35 (по умолчанию 1.35); конформность 1.36 с сентября 2026 г. | Три последних минора; версия по умолчанию 1.35, 1.36 или 1.37 [расхождение] |
| Обновления | Вручную, сначала CP, потом группы; принудительно по EOL (по прото) | Вручную, +1 минор за раз; узлы перезагружать по пулам |
| Endpoint API | Публичный по умолчанию + IPv4-allowlist; приватный есть всегда | Публичный по умолчанию; приватный — через поддержку; Tailscale |
| Аудит | Включается явно; логи CP пишутся всегда | `audit_policy`, Telemetry Relay |
| Подложка | VM | Bare metal без гипервизора |
| GPU | H100, H200, L40S, RTX PRO 6000, B200, B300, GB300 NVL72 | H100, H200, RTX PRO 6000, B200, B300, GB200/GB300 NVL72, Vera Rubin NVL72 (limited availability) |
| NVL72 | 18 × `4gpu-112vcpu-800gb`, NVLink group, `max_surge=0` | 18 × (4 GPU, 144 vCPU, 960 ГБ), `targetRacks` |
| Ёмкость | On-demand, preemptible (динамический spot с октября 2026 г.), capacity blocks с откатом на PAYG | Reservations, Flex, On-Demand, Spot (с марта 2026 г., 7 мин на остановку) |
| Драйверы | Образы с драйверами (`drivers_preset`) или свой GPU Operator; DRA | Предустановлены; GPU Operator управляется CoreWeave |
| Интерконнект и топология | IB через GPU cluster (только 8-GPU узлы); метки топологии | IB через Multus; метки NVLink/rack/IB; Kueue TAS по умолчанию |
| MIG | Через GPU Operator в Terraform-решении | Не документирован |
| Проверки здоровья | NPD; пассивные GPU-проверки раз в 5 мин; активные — preview | Пассивные — непрерывно; HPC Verification — ежечасно на простаивающих узлах |
| Ремонт | Кордон → дренаж до 1 ч → stop/start VM; замена узла | Перезагрузка или триаж с автозаменой; `prefill` |
| Автомасштабирование | Cluster Autoscaler, до 100 узлов на группу; Karpenter в API | Cluster Autoscaler, K8s 1.32+; не для NVL72 и SUNK |
| CNI и сеть | Cilium + CoreDNS; LB через аннотации `nebius.com/...` | Cilium 1.18.1 (управляется CoreWeave), LB IPAM, NAT egress, без FQDN-политик |
| Общие ФС | Shared FS через virtiofs + CSI (с ограничениями) | DFS (VAST), класс `shared-vast`, RWX |
| Объектное хранилище | Mountpoint for S3 CSI | AI Object Storage + LOTA, без платы за egress |
| Slurm | Soperator (open source; Managed и Pro) | SUNK, SUNK Self-Service |
| Аддоны | Каталог Applications (~37 приложений) | CoreWeave Charts, `cks-kueue` |
| Наблюдаемость | O11y agent, Prometheus/Loki-совместимые API, Grafana | Managed Grafana (без своих алертов), PromQL/LogQL, Telemetry Relay, W&B |
| Регионы | 7 регионов в ЕС, Великобритании и США | Суперрегионы US-EAST/CENTRAL/WEST, EU-NORTH/SOUTH/WEST |
| Квоты | 2 кластера на регион, 1000 аллокаций, 100 узлов на группу | Организация × тип × compute class × зона; заполнение до квоты |
| ClusterMAX 3.0 | Platinum (раньше Gold) | Platinum (третий раз подряд) |

## Двенадцать фактов, которые нельзя брать на веру

Большая часть противоречий приходится на факты из документации, полученные через поисковые выдержки. Факты из прото и исходников провайдеров стабильнее. Если документация и прото расходятся, поведение API лучше определять по прото. Но ограничения уровня политики (например, «только через поддержку») могут действовать как организационный барьер поверх API.

| Тема | Что говорят источники | Как проверить |
|---|---|---|
| Версия K8s по умолчанию в CKS | Одна выдержка из матрицы — v1.34–v1.36 (по умолчанию 1.36), другая — v1.35–v1.37 (по умолчанию 1.37); changelog — 1.35 или 1.36; пример Terraform — v1.35 | Живая страница Cluster components или консоль |
| Поддерживаемые версии Nebius | Документация — 1.33–1.35; Aether 3.6 (июнь 2026 г.) — 1.34; CNCF — сертифицирована 1.36 (сентябрь 2026 г.) | Вызов `ListControlPlaneVersions` |
| Автообновление Nebius | Документация — автообновления нет; прото — версия «forced out of use» по `end_of_life`, а фактическая версия может обогнать желаемую | Поле `end_of_life` в `ListControlPlaneVersions` |
| Karpenter в Nebius | В найденной документации нет; в прото поле `karpenter` есть с 16 марта 2026 г. | `PreflightCheck`/`Create` с `karpenter: {}` на тестовом проекте |
| Приватные кластеры CKS | Документация — только через поддержку; в API `public` помечено OPTIONAL, а пример Terraform ставит `public = false` | Тестовое создание с `public = false` |
| Spot compute class в CKS | Справочник NodePool — только `default`; документация по Spot — `spot` или `Spot` | `kubectl explain nodepool.spec.computeClass` в кластере |
| Обязательные поля `CreateCluster` | Одна страница — 7 полей; другая добавляет `public`, `oidc.issuerUrl` и вебхуки; прото помечает их OPTIONAL | Ориентироваться на прото |
| Флаг обновляемости CKS | Документация — `isUpgradable`; JSON-имя в прото — `isUpgradeable` | JSON-ответ `GetCluster` |
| Атрибуты Terraform Nebius | `underlay_required`, `preemptible.on_preemption` и `priority` есть в примерах, но отсутствуют в публичном прото | `terraform providers schema` |
| Spot-цены Nebius | btw.media — динамика от $0,79/$0,99; прайс — $0,95 (RTX PRO 6000) и $0,65 (L40S); старые цены от $2,45 устарели | Консоль или прайс в день закупки |
| SLA control plane CKS | Официального SLA не найдено; сторонний обзор называет 99,9%/99% | Договор или запрос в отдел продаж |
| Рейтинг Nebius | ClusterMAX 3.0 — Platinum; страница clustermax.ai всё ещё озаглавлена «Gold Tier» | Полный текст ClusterMAX 3.0 |

Кроме того, остаются открытыми ещё несколько вопросов:
- работает ли CKS `v1beta2` на проде;
- как роли IAM Nebius отображаются на RBAC в кластере;
- имена меток IB-топологии у Nebius;
- соответствие пресетов `cuda12.8`/`cuda13.0` драйверам 570.x/580.x — это соответствие взято лишь из неофициальных заметок ([community notes](https://github.com/sanketsultan/nebius-cloud-ops-cert-prep/blob/master/notes/02-gpu-clusters.md)).

## Практические выводы для инструмента под оба провайдера

| Задача | Nebius mk8s | CoreWeave CKS |
|---|---|---|
| Создать кластер | `ClusterService.Create` → `Operation`, ждать появления `status` | Сначала VPC, затем `CreateCluster` → опрос `GetCluster` до `STATUS_RUNNING` (до 45 мин) |
| Создать пул узлов | `NodeGroupService.Create` в том же API и с тем же токеном | Получить kubeconfig → `kubectl apply` для CRD `NodePool` |
| Масштабировать | `Update` с `fixed_node_count` или `autoscaling` | `kubectl scale nodepool --replicas N` или правка `targetNodes` |
| Признак готовности | Операция завершена, `ready_node_count` = `target_node_count`, `reconciling = false` | Условия `Validated`/`Capacity`/`Quota`/`AtTarget`, `currentNodes` |
| Обновить K8s | `Update` поля `spec.control_plane.version`, затем `Update` поля `spec.version` каждой группы | `PATCH version` на +1 минор при `is_upgradeable`, затем перезагрузка узлов по пулам |
| Получить kubeconfig | `nebius mk8s cluster get-credentials` (exec-плагин → IAM) | Консоль или `cwic cluster auth` (только для публичных кластеров) |
| Безопасный повтор | `X-Idempotency-Key` + `retry_type` | В v1beta1 нет; перед повтором `Create` проверять наличие кластера по имени |
| Очистить поле | `X-ResetMask` (SDK ставит сам) | `update_mask` и только изменённые поля |
| Узнать доступные версии | `ListControlPlaneVersions`, `GetCompatibilityMatrix` | RPC нет; по документации — три последних минора |

Главное архитектурное решение — **два контура согласования вместо одного**. Для Nebius один gRPC-клиент (gosdk или pysdk) с одним IAM-токеном покрывает и кластер, и группы узлов. Для CoreWeave нужны два клиента: Connect/REST — для кластера и VPC, и Kubernetes — для `NodePool`. Kubernetes-клиент появляется только после того, как кластер дошёл до `RUNNING` и стал доступен по сети. В Terraform это превращается в связку провайдеров `coreweave` и `kubernetes` с двухфазным apply, как в собственном репозитории CoreWeave ([reference-architecture nodepool/main.tf](https://github.com/coreweave/reference-architecture/blob/main/terraform/modules/nodepool/main.tf)).

Абстракцию разумно строить вокруг «желаемого состояния группы узлов» с двумя адаптерами. Адаптер Nebius ждёт `Operation` и честно получает отказ при конфликте. Адаптер CoreWeave применяет манифест и читает условия. У CoreWeave нужно особо следить, что заполнение пула ниже цели из-за квоты — это не ошибка, а нормальное состояние с условием `Quota`. Такое состояние надо явно поднимать как предупреждение.

Второй слой — **семантика записи**, и здесь провайдеры требуют противоположного. Nebius ожидает полный желаемый spec: Get → изменение → Update с `resource_version`. Явное обнуление полей с `MEANINGFUL_EMPTY_VALUE` (например, снятие публичного endpoint или `audit_logs`) делается только через `X-ResetMask`. CoreWeave ожидает минимальный diff: только изменённые поля, всегда с обязательным `version` и без `network` при обновлении версии. Писателей на стороне CoreWeave нужно сериализовать самостоятельно, раз etag нет.

Таблицу неизменяемых полей инструмент тоже должен вести сам:
- у CoreWeave — `name`, `zone`, `vpc_id`, имена pod/service CIDR, `shared_storage_cluster_id`; плюс правила «только дополнять» для LB-CIDR и «только расширять» для NodePort;
- у Nebius — `subnet_id`, `service_cidrs`, `nvl_instance_group_id`, а по документации ещё платформа, пресет и GPU cluster группы.

У Nebius часть этой работы берёт на себя `PreflightCheck` с `paths_require_recreate`. У CoreWeave аналога нет.

Валидацию стоит строить по общему знаменателю:
- имена — не длиннее 30 символов в нижнем регистре в формате DNS-метки;
- версии — в двух форматах, `"1.35"` и `"v1.35"`;
- группы Nebius — шардировать по 100 узлов;
- метки и taints у Nebius не доходят до существующих узлов, поэтому их нужно проставлять через Kubernetes API или принудительной раскаткой;
- стратегию раскатки Nebius задавать явно, потому что значения по умолчанию сменились в Q3 2026, а у групп с NVLink `max_surge` обязан быть 0.

С резервациями Nebius есть ловушка: surge-узел сверх резерва при `AUTO` уходит в PAYG, а при `STRICT` останавливает раскатку ([PR #1091](https://github.com/nebius/nebius-solutions-library/pull/1091)).

Третий слой — **учётные данные и здоровье**. Для Nebius CI-раннеру нужен ключ сервисного аккаунта, из которого получается короткоживущий IAM-токен, и CLI `nebius` в образе для exec-плагина kubeconfig. Для CoreWeave до осени 2026 г. единственным вариантом был пользовательский `CW-SECRET`. Теперь стоит переходить на Workload Federation и обновляемые токены из провайдера v0.23.0+, а для приватных кластеров опираться на OIDC, который `cwic cluster oidc config` настраивает и для них.

Сигналы здоровья нужно нормализовать в общий словарь:
- у Nebius — условия узла `NebiusGPUError` и `NebiusBootDiskIOError`, а также `events` группы;
- у CoreWeave — метки `node.coreweave.cloud/state` и `/pending-state` и условия `CWActive` и `PendingPhaseState`.

Не стоит бороться с автоматикой платформы. Nebius пересоздаёт узлы с новыми именами, поэтому привязываться к именам узлов нельзя. CoreWeave может снять ручной cordon, поэтому изоляцию надо делать собственным taint. Если узлами управляет свой оркестратор вроде Slurm, авторемонт Nebius стоит отключить для отдельных условий.

Наконец, контракты стоит закреплять на конкретных ревизиях и прогонять против них тесты: прото Nebius (анализ шёл по коммиту `635d8b6`) и Buf-модуля CoreWeave (`77e2aa82efe3`). Оба API за 2026 г. менялись ежемесячно.

## Заключение

Провайдеры сближаются с противоположных концов. Неопубликованный CKS `v1beta2` добавляет ровно то, что в Nebius `v1` уже есть: `request_id`, `conditions`, канонические имена, фильтры в List и Metadata-сообщения, намекающие на будущие длительные операции. Nebius за 2026 г. добавил в API то, чем силён продукт CoreWeave: DRA, стоечные группы NVLink, Karpenter, ценовую модель и резервации прямо в группе узлов. Поэтому различия на уровне RPC — временные. Устойчивых различий два. Первое — место жизненного цикла узлов: облачный API против CRD внутри кластера с автоматикой платформы. Второе — семантика ремонта: у Nebius это stop/start VM, после которого появляется новый узел с новым именем, у CoreWeave — перезагрузка или замена bare-metal узла с состоянием в метках. Именно здесь, а не в обёртках над gRPC и Connect, стоит вкладываться в абстракцию. RPC-адаптеры лучше держать тонкими и заменяемыми, потому что переход CoreWeave на `v1beta2` их перепишет.

Для выбора это означает компромисс между прозрачностью и готовностью. Nebius даёт инструменту больше опор:
- SLA;
- объекты операций;
- `PreflightCheck`;
- сервисные аккаунты;
- поддерживаемые SDK.

Взамен он требует самому владеть частью GPU-стека и успевать за быстро меняющимся прото. CoreWeave даёт «из коробки» bare metal, управляемый GPU Operator, Kueue с учётом топологии, активные проверки и LOTA. Расплачиваться приходится причудами beta-API, пользовательскими токенами, двухконтурной инфраструктурой как кодом и меньшим контролем над драйверами и CNI. Раз оба провайдера теперь в Platinum, качество кластера перестаёт быть решающим аргументом; решает то, какую операционную нагрузку команда готова взять на себя. Перед окончательным выбором стоит проверить три спорных пункта: приватные кластеры CKS, принудительные обновления Nebius и статус Karpenter. Каждый из них меняет дизайн автоматизации.
