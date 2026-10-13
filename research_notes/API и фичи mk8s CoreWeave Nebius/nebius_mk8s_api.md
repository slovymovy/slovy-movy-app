# Nebius AI Cloud Managed Service for Kubernetes (mk8s) — public API and tooling (state as of 2026-10-13)

Method note: primary source is the proto repo `github.com/nebius/api`, cloned at commit `635d8b6` (2026-10-13, "Publish proto files from 9f6a73b137"), with full git history used to date field introductions. Also used: `github.com/nebius/gosdk` (2026-10-13), `github.com/nebius/pysdk`, and `github.com/nebius/nebius-solution-library` (official Terraform examples, 2026-10-13). `docs.nebius.com` and `registry.terraform.io` could not be fetched from this environment: the egress proxy returned 403, and WebFetch could not resolve the host. Facts attributed to docs.nebius.com come from search-engine snippets of those pages and are marked "(via search snippet)". Weight them below the proto/SDK facts.

API version labels used below: **[v1]** = `nebius.mk8s.v1` (current). **[v1alpha1]** = `nebius.mk8s.v1alpha1` (deprecated). **[common.v1]** = `nebius.common.v1` shared types.

## 1. API style, transport, endpoints, versions

### Takeaway
mk8s is a gRPC/protobuf-only API. It is served at `mk8s.api.nebius.cloud:443`, which hosts both `nebius.mk8s.v1` (current) and `nebius.mk8s.v1alpha1` (deprecated, "migrate to v1"). It exposes two services, `ClusterService` and `NodeGroupService`, plus the generic `OperationService` on the same host.

### Cited Findings
- The repo "contains the `.proto` files defining the gRPC API for Nebius AI Cloud services". Services are reached at `{service-name}.api.nebius.cloud:443`, where `{service-name}` is the proto's `option (api_service_name)`. — [nebius/api README](https://github.com/nebius/api/blob/main/README.md)
- Both mk8s services declare `option (api_service_name) = "mk8s";`. — [cluster_service.proto v1](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster_service.proto); [node_group_service.proto v1](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group_service.proto)
- The endpoint list shows `mk8s.api.nebius.cloud:443` serving these services:
  - `nebius.common.v1.OperationService`
  - `nebius.common.v1alpha1.OperationService`
  - `nebius.mk8s.v1.ClusterService`
  - `nebius.mk8s.v1.NodeGroupService`
  - `nebius.mk8s.v1alpha1.ClusterService`
  - `nebius.mk8s.v1alpha1.NodeGroupService`

  — [endpoints.md](https://github.com/nebius/api/blob/main/endpoints.md)
- `OperationService` "does not have a standalone endpoint". You query it on the endpoint of the service that created the operation, which for mk8s is `mk8s.api.nebius.cloud:443`. — [README](https://github.com/nebius/api/blob/main/README.md)
- Every v1alpha1 mk8s file carries `option deprecated = true;` and `(file_deprecation_details) = {description: "migrate to v1"}`.
  - The services add "Cluster v1alpha1 API is deprecated, please migrate to v1." and "NodeGroup v1alpha1 API is deprecated, please migrate to v1."
  - Git history shows the deprecation marker was added on 2025-11-10.

  — [v1alpha1 cluster_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1alpha1/cluster_service.proto); [v1alpha1 node_group_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1alpha1/node_group_service.proto)
- v1 and v1alpha1 both first appeared in the public repo on 2024-09-17. The repo's first commit is 2024-09-10. — [commit d478ba0](https://github.com/nebius/api/commit/d478ba0)
- v1alpha1 services return `common.v1alpha1.Operation`; v1 returns `common.v1.Operation`. v1alpha1 `GetByName` takes service-specific `GetClusterByNameRequest`/`GetNodeGroupByNameRequest`, while v1 uses the shared `common.v1.GetByNameRequest`. — [v1alpha1 cluster_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1alpha1/cluster_service.proto); [v1 cluster_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster_service.proto)
- Field-level diff (computed from the current protos). These items exist in **[v1] only**:
  - `allowed_cidrs`, `audit_logs`, `karpenter`
  - control-plane version metadata `restricted` / `end_of_life` / `deprecated`
  - `disk_encryption`, `local_disks`, `nvlink`, `reservation_policy`, `max_pods`
  - pricing model `on_demand` / `follows_spot_price` / `spot_pricing_policy`
  - `security_groups`, `instance_metadata`, `gpu_settings.dra`
  - RPCs `PreflightCheck` and `GetCompatibilityMatrix`

  In [v1alpha1], filesystem attachments use `device_name`, where v1 uses `mount_tag`. — [v1 node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto); [v1alpha1 node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1alpha1/node_group.proto)
- Proto options: `go_package = "github.com/nebius/gosdk/proto/nebius/mk8s/v1"` and `java_package = "ai.nebius.pub.mk8s.v1"`. Validation uses `buf.validate` (protovalidate) CEL rules embedded in the protos. — [cluster.proto v1](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto)
- Protos are published by automated "Publish proto files from <hash>" commits, five of them between 2026-10-05 and 2026-10-13. The last mk8s/v1 change landed 2026-09-28. — [nebius/api commits](https://github.com/nebius/api/commits/main)
- The gosdk default domain is `api.nebius.cloud:443` (`constants.Domain`), with a `WithDomain` option to change it. — [gosdk constants.go](https://github.com/nebius/gosdk/blob/main/constants/constants.go); [gosdk options.go](https://github.com/nebius/gosdk/blob/main/options.go)

### Inferences
- The service hostname is region-agnostic (`mk8s.api.nebius.cloud`, with no region in it). Region placement presumably follows from the parent project/subnet and the region "routing code" embedded in IDs (see section 2).
- No REST/JSON transcoding (`google.api.http`) annotations exist in the mk8s protos. A REST client therefore has no documented path; gRPC (or the SDK/CLI/Terraform, which all use gRPC) is the integration surface.
- "v1" is the de-facto GA version. No v1beta exists. All new fields since late 2025 have landed only in v1.

### Gaps
- No official stability/SLA statement for v1 (e.g., a "GA" label or a deprecation policy window) was found in the repo. docs.nebius.com was not reachable to confirm.
- No removal date for v1alpha1 was found: `DeprecationDetails.effective_at` is not set in the mk8s protos.

## 2. Common resource model (metadata, spec/status, hierarchy, IDs, naming)

### Takeaway
Every resource is `{metadata: common.v1.ResourceMetadata, spec, status}`.
- **Hierarchy:** tenant → project → `mk8scluster` → `mk8snodegroup`. A cluster's `parent_id` is a project; a node group's `parent_id` is a cluster.
- **IDs:** typed NIDs of the form `<type>-<3-char routing code><weak id>`.
- **Concurrency:** optimistic locking via `resource_version`, which increments only on spec changes.

### Cited Findings
- `ResourceMetadata` [common.v1] has these fields:

  | Field | Notes |
  | --- | --- |
  | `id` (1) | `IDENTIFIER` |
  | `parent_id` (2) | required |
  | `name` (3) | max 1024 bytes |
  | `resource_version` (4) | int64 ≥ 0 |
  | `created_at` (5) | `OUTPUT_ONLY` |
  | `updated_at` (6) | `OUTPUT_ONLY` |
  | `labels` (7) | `map<string,string>` |

  — [metadata.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/metadata.proto)
- `resource_version` is the "Version of the resource for safe concurrent modifications and consistent reads. Positive and monotonically increases on each resource spec change (but *not* on each change of the resource's container(s) or status). Service allows zero value or current." — [metadata.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/metadata.proto)
- `GetByNameRequest{parent_id, name}` is used "in services supporting uniqueness of `ResourceMetadata.name` within tuple (scope) <resource_type, `parent_id`>". — [metadata.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/metadata.proto)
- [v1] `Cluster{metadata=1, spec=2 (ClusterSpec), status=3 (ClusterStatus)}` with `(nid) = {parent_resource: ["project"]}`. `NodeGroup{metadata, spec, status}` with `parent_resource: ["mk8scluster"]`. — [cluster.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto); [node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)
- [v1] Naming rules:
  - **Cluster `name`:** "1 to 63 characters long and use only letters, digits, '-', or '_', starting and ending with a letter or digit". Regex: `^(([A-Za-z0-9][-A-Za-z0-9_]*)?[A-Za-z0-9])?$`.
  - **Cluster `parent_id`:** ≤ 63 characters.
  - **Node group `name`:** same length limit, but the regex also allows `.` (`[-A-Za-z0-9_.]`).

  — [cluster_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster_service.proto); [node_group_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group_service.proto)
- `(nid)` resource-type annotations name the referenced types: `project`, `mk8scluster`, `mk8snodegroup`, `vpcsubnet`, `vpcsecuritygroup`, `serviceaccount`, `computegpucluster`, `computefilesystem`, `computenvlinstancegroup`, `computereservation`, `pricingpolicy`. These typed annotations replaced plain string IDs in mk8s on 2026-07-08. — [node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto); [commit bbb05e4](https://github.com/nebius/api/commit/bbb05e4)
- NID grammar (gosdk):
  - Overall regex: `^([a-z][a-z0-9]{2,49})-([a-z][a-z0-9]{2})(.+?)(?:--([a-z-][a-z0-9-]{0,9}))?$`. That is `type`-`routingCode`(3 chars)`weakID`[`--reserved`], max length 1024.
  - The gosdk comment links routing codes to regions ("List of routing codes here: …/iam/about-iam/regions").
  - Default routing code is `e0t` (deprecated constant).

  — [gosdk nid/patterns.go](https://github.com/nebius/gosdk/blob/main/nid/patterns.go); [gosdk nid/parts.go](https://github.com/nebius/gosdk/blob/main/nid/parts.go)
- Example IDs in the official README: `serviceaccount-e00...`, `project-e00...`, `publickey-e00...`, `federation-e00...`, `useraccount-e00...`. — [README](https://github.com/nebius/api/blob/main/README.md)
- Tenants and projects are IAM resources at `cpl.iam.api.nebius.cloud:443`. Services there include `nebius.iam.v1.TenantService`, `nebius.iam.v1.ProjectService`, `nebius.iam.v2.TenantService` and `nebius.iam.v2.ProjectService`. — [endpoints.md](https://github.com/nebius/api/blob/main/endpoints.md)
- `FieldBehavior` annotations used throughout:

  | Annotation | Meaning |
  | --- | --- |
  | `IMMUTABLE` | Change → `INVALID_ARGUMENT`; "Resource recreate requires a change of the field value" |
  | `IDENTIFIER` | Must be present on update, must not be set on create |
  | `OUTPUT_ONLY` | Cannot be set on create or changed on update |
  | `MEANINGFUL_EMPTY_VALUE` | "its empty message enables that feature, and null disables it" |
  | `NON_EMPTY_DEFAULT` | "an empty (default) value will be filled by the server"; the docs warn Terraform may not detect explicit→default changes |

  — [annotations.proto](https://github.com/nebius/api/blob/main/nebius/annotations.proto)
- Status objects carry `repeated common.v1.RecurrentResourceEvent events` (added 2025-09-25). Each event has `first_occurred_at`, `last_occurrence{occurred_at, level DEBUG/INFO/WARN/ERROR, code, message, error google.rpc.Status}` and `occurrence_count`. — [resource_event.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/resource_event.proto); [commit b582af3](https://github.com/nebius/api/commit/b582af3)
- Both statuses have `bool reconciling = 100` ("Show that changes are in flight"). — [cluster.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto)

### Inferences
- mk8s IDs therefore look like `mk8scluster-e00…` / `mk8snodegroup-e00…`, following the documented `project-e00…` pattern. No mk8s ID sample was found verbatim.
- Cluster names are unique per project and node-group names per cluster. `GetByName` is scoped by `parent_id`.
- `metadata.labels` are Nebius resource labels and are distinct from Kubernetes node labels (`template.metadata.labels`). In Terraform they are flattened to a top-level `labels` attribute (see section 8).
- Neither state enum has an ERROR/FAILED value. Failures surface via operations, `events` (level `ERROR`), and `reconciling`.

### Gaps
- No verbatim mk8s ID example was found.
- The exact mapping of routing codes to regions could not be checked: the referenced docs page was unreachable.

## 3. Services and RPCs

### Takeaway
[v1] exposes two services with standard CRUD plus a few version and validation helpers:
- **ClusterService:** Get, GetByName, List, Create, Update, Delete and ListControlPlaneVersions.
- **NodeGroupService:** Get, GetByName, List, Create, Update, Delete, Upgrade, PreflightCheck (new, 2026-09) and GetCompatibilityMatrix.

There is no separate versions service, and no cluster-level Upgrade RPC.

### Cited Findings
- [v1] `ClusterService` RPCs:

  | RPC | Request → response | Notes |
  | --- | --- | --- |
  | `Get` | `GetClusterRequest{id, resource_version}` → `Cluster` | |
  | `GetByName` | `common.v1.GetByNameRequest` → `Cluster` | |
  | `List` | `ListClustersRequest{parent_id (project), page_size, page_token}` → `{items, next_page_token}` | |
  | `Create` | `CreateClusterRequest{metadata (required), spec (required)}` → `common.v1.Operation` | |
  | `Update` | `UpdateClusterRequest{metadata (required), spec}` → `Operation` | |
  | `Delete` | `DeleteClusterRequest{id}` → `Operation` | |
  | `ListControlPlaneVersions` | `{}` → `{items: ClusterControlPlaneVersion[]}` | Added 2025-09-11 |

  — [cluster_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster_service.proto); [commit 136a7e8](https://github.com/nebius/api/commit/136a7e8)
- `ClusterControlPlaneVersion` fields:
  - `version`: "includes only MAJOR.MINOR … For example "1.31"".
  - `restricted`: "versions available only for internal Nebius use … a feature restricted_k8s_versions should be enabled on a cluster". Added 2026-05-04.
  - `end_of_life` (Timestamp): "when the version will be forced out of use".
  - `deprecated`: "Creation of clusters with this version is forbidden".

  — [cluster_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster_service.proto); [commit 2a50a88](https://github.com/nebius/api/commit/2a50a88)
- [v1] `NodeGroupService` RPCs:

  | RPC | Request → response | Notes |
  | --- | --- | --- |
  | `Get` | `{id, resource_version}` | |
  | `GetByName` | `common.v1.GetByNameRequest` | |
  | `List` | `{parent_id (mk8scluster), page_size, page_token}` | |
  | `Create` | `{metadata, spec}` | |
  | `Update` | `{metadata, spec}` | |
  | `Delete` | `{id}` | |
  | `Upgrade` | `UpgradeNodeGroupRequest{id, oneof upgrade_type{google.protobuf.Empty latest_infra_version}}` | |
  | `PreflightCheck` | `{context, metadata, spec}` → `{diagnostics[], paths_require_recreate, requires_user_approval}` | Added 2026-09-16 |
  | `GetCompatibilityMatrix` | `{cluster_kubernetes_version (required), platform}` → `{versions[{kubernetes_version, items[{os, drivers_preset, compatible_platforms[]}]}]}` | Added 2025-11-11 |

  — [node_group_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group_service.proto); [commit 1396e2a](https://github.com/nebius/api/commit/1396e2a); [commit 03c83a7](https://github.com/nebius/api/commit/03c83a7)
- `Upgrade.latest_infra_version`: "Upgrades to the latest infra version, which includes latest supported kubernetes patch version. Kubernetes minor version remain the same." — [node_group_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group_service.proto)
- `PreflightCheckContext`:
  - `action` is one of `UNSPECIFIED` ("Infer UPDATE when the request metadata contains a resource ID; otherwise infer CREATE"), `CREATE`, `UPDATE`, `DELETE` ("Reserved until delete preflight requests are supported") or `RECREATE`.
  - `tool` is a "Lower-case name of the calling tool, such as "terraform", "cli", or "console"".
  - `unknown_paths` is a Nebius mask of values unknown at plan time.
  - `paths_require_recreate` is listed as well.
  - `PreflightCheckDiagnostic{severity ERROR|WARNING, summary, details, path}`.

  — [preflight_check.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/preflight_check.proto)
- `OperationService` [common.v1] has `Get(GetOperationRequest{id})` and `List(ListOperationsRequest{resource_id, page_size [1..1000], page_token})` → `{operations, next_page_token}`. — [operation_service.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/operation_service.proto)
- The gosdk mk8s v1 wrappers add `Filter(...) iter.Seq2[*NodeGroup, error]`, an auto-paginating iterator, plus `GetOperation`/`ListOperations` on each service. — [gosdk node_group_service.sdk.go](https://github.com/nebius/gosdk/blob/main/services/nebius/mk8s/v1/node_group_service.sdk.go)

### Inferences
- **Version changes:**
  - Control-plane minor version upgrades are done via `ClusterService.Update` with a new `spec.control_plane.version`; the field is not `IMMUTABLE`.
  - Node group minor version changes go via `NodeGroupService.Update` (`spec.version`).
  - `NodeGroupService.Upgrade` is only for "latest infra/patch within same minor".
- `List` has no server-side filter or label-selector field: only `parent_id` plus pagination. Clients filter locally.
- `Delete` takes no `resource_version`, so delete is not concurrency-guarded by version. A delete is also not cascading by declaration. Whether a cluster with node groups can be deleted directly is not specified in the protos.
- `PreflightCheck` (with `tool="terraform"`, `unknown_paths`, `paths_require_recreate`, `requires_user_approval`) looks designed for Terraform/CLI plan-time validation and recreate detection.

### Gaps
- Whether `ClusterService.Delete` cascades to node groups is not stated in the protos.
- No public "UpgradeCluster" or maintenance-window RPC exists in mk8s. There is a separate `nebius.maintenance.v1alpha1.MaintenanceService` at `maintenance.msp.api.nebius.cloud` ([endpoints.md](https://github.com/nebius/api/blob/main/endpoints.md)), but its relation to mk8s was not verified.

## 4. Cluster spec and status fields [v1]

### Takeaway
`ClusterSpec` is minimal. It has two parts:
- `control_plane`: version, subnet, optional public endpoint with an IPv4 allow-list, etcd size 1 or 3, audit logs, Karpenter.
- `kube_network`: a single immutable service CIDR. There is **no pod CIDR field** anywhere in the API.

Status returns state, the actual version string, public/private endpoints, the CA cert, events and `reconciling`.

### Cited Findings
- `ClusterSpec{control_plane ControlPlaneSpec = 2 (required); kube_network KubeNetworkSpec = 3}`. — [cluster.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto)
- `ControlPlaneSpec`, field by field ([cluster.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto)):
  - `version` (1): "Desired Kubernetes version … May be lower than the actual cluster version if the desired version is no longer supported and the cluster has been automatically updated. For now only acceptable format is `<major>.<minor>`". Pattern `|^\d\.\d\d$`.
  - `subnet_id` (2): required, `IMMUTABLE`, type `vpcsubnet`. "Nebius VPC Subnet ID where control plane instances will be located. Also will be default NodeGroup subnet."
  - `endpoints` (3) → `ControlPlaneEndpointsSpec{public_endpoint PublicEndpointSpec (MEANINGFUL_EMPTY_VALUE)}`. "When set, a public endpoint is created."
  - `etcd_cluster_size` (4): int64 in {0,1,3}. "3 by default. Control plane with `etcd_cluster_size: 3` called 'Highly Available' ('HA')."
  - `audit_logs` (5): `AuditLogsSpec{}` (MEANINGFUL_EMPTY_VALUE). "Specify configuration of the pushing k8s audit logs into service logs and show it in the UI. By default cluster will be created without it." Added 2026-01-26 ([commit 835ef38](https://github.com/nebius/api/commit/835ef38)).
  - `karpenter` (6): `Karpenter{}` (MEANINGFUL_EMPTY_VALUE). "Enables installation of the Karpenter inside a cluster … installed as a helm chart … requires creation of at least one CPU public node group … there is no feature parity between Karpenter node pools and public node groups." Added 2026-03-16 ([commit 4bcb49f](https://github.com/nebius/api/commit/4bcb49f)).
- `PublicEndpointSpec.allowed_cidrs` (2): "If field is not set, or list is empty, it means that access is not restricted at all. You can specify a maximum of 8 CIDRs." The values are IPv4 prefixes only (`isIpPrefix(4, true)`). Added 2026-06-16. — [cluster.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto); [commit 5a0adcf](https://github.com/nebius/api/commit/5a0adcf)
- `KubeNetworkSpec.service_cidrs` (1): "CIDR blocks for Service ClusterIP allocation. For now, only one value is supported. Must be a valid CIDR block or prefix length … auto allocated … reserved in Cluster.spec.control_plane.subnet_id … Allowed prefix length is from "/12" to "/28". Empty value treated as ["/16"]." It is `IMMUTABLE`, with `max_items: 1`. — [cluster.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto)
- `ClusterStatus` ([cluster.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto)):
  - `state`: `STATE_UNSPECIFIED`/`PROVISIONING`/`RUNNING`/`DELETING`.
  - `control_plane ControlPlaneStatus`.
  - `events`.
  - `reconciling` (100).
- `ControlPlaneStatus` ([cluster.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto)):
  - `version` has the format `<major>.<minor>.<patch>-nebius-cp.<infra_version>`, e.g. "1.30.0-nebius-cp.3". The infra version covers "bug fixes, security updates and new features of components running on control plane, like CCM or Cluster Autoscaler".
  - `endpoints{public_endpoint ("accessible from the Internet"), private_endpoint ("accessible from the user VPC")}`. "Kubernetes API can be accessed at `https://endpoint/`."
  - `etcd_cluster_size`.
  - `auth{cluster_ca_certificate}`: a PEM CA.
- Terraform example (official solution library), cluster resource HCL:

  ```hcl
  resource "nebius_mk8s_v1_cluster" "x" {
    parent_id = ...
    name      = ...
    control_plane = {
      endpoints         = { public_endpoint = {} }
      etcd_cluster_size = ...
      subnet_id         = ...
      version           = ...
    }
  }
  ```

  The Soperator module also sets `audit_logs = {}`. — [k8s-training/main.tf](https://github.com/nebius/nebius-solution-library/blob/main/k8s-training/main.tf); [soperator/modules/k8s/k8s_cluster.tf](https://github.com/nebius/nebius-solution-library/blob/main/soperator/modules/k8s/k8s_cluster.tf)

### Inferences
- **Private endpoint:** the API has no toggle for it. It appears always to exist (status `private_endpoint`); only the public endpoint is optional.
- **Pod networking:** the API has no pod CIDR, CNI or cluster-autoscaler settings. Pod addressing is driven per node by `NodeTemplate.max_pods` (section 5). The cluster autoscaler is enabled implicitly per node group via `autoscaling`. A comparator should note that pod CIDR, CNI choice, OIDC/auth config, add-ons, feature gates and API-server flags are not user-configurable via this API.
- **etcd size:** `etcd_cluster_size: 0` is allowed by validation and presumably means "server default (3)", since the comment says "3 by default".
- **Version strings:** desired version is `MAJOR.MINOR` only, while actual status carries `x.y.z-nebius-cp.N`. Auto-upgrades can move the actual version past the desired one.

### Gaps
- Default/recommended Kubernetes versions as of Oct 2026 could not be read from the API itself (they come from `ListControlPlaneVersions` at runtime). A docs changelog snippet says "Kubernetes 1.35 … is now the default and recommended Kubernetes version", but the snippet's date was unclear (~310 days old, i.e. roughly Dec 2025). — [Nebius changelog (via search snippet)](https://docs.nebius.com/changelog)
- No IPv6/dual-stack fields exist. Whether this is unsupported or just unexposed is not stated.

## 5. NodeGroup spec and status fields [v1]

### Takeaway
`NodeGroupSpec` is `{version, oneof size{fixed_node_count | autoscaling{min,max}}, template, strategy, auto_repair}`. Sizes are capped at 0–100 nodes per group. `template` is a VM template covering:
- platform/preset, boot disk, OS, GPU drivers preset/DRA, GPU cluster (InfiniBand), NVLink group, single NIC with optional public IP and security groups;
- filesystem attachments, cloud-init, service account, preemptible/spot pricing, capacity-block reservation policy, local NVMe disks, `max_pods`.

Many of these fields were added between 2025 and 2026.

### Cited Findings
- `NodeGroupSpec` ([node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)):
  - `version` (1): pattern `^$|^\d+\.\d+(-nebius-node\.[1-9]\d*)?$`. "By default the cluster control plane `<major>.<minor>` version will be used."
  - `oneof size` (required):
    - `fixed_node_count` (2): 0..100. "Can be changed manually at any time, except for a node group with NVLink."
    - `autoscaling` (5): `NodeGroupAutoscalingSpec{min_node_count, max_node_count}`, each 0..100, with min ≤ max. It "Enables Kubernetes Cluster Autoscaler for that NodeGroup".
  - `template` (3, required).
  - `strategy` (4).
  - `auto_repair` (6), added 2025-11-11.
- Message-level CEL rules: "autoscaling cannot be used for a node group with NVLink" and "max_surge must be 0 for a node group with NVLink". — [node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)
- `NodeTemplate` fields in proto field-number order ([node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)):
  - `metadata` (1): `NodeMetadataTemplate{labels map ≤100}`. These are Kubernetes Node labels. "change will not be propagated to existing nodes … System labels containing "kubernetes.io" and "k8s.io" will be ignored. Field change will NOT trigger NodeGroup roll out."
  - `taints` (2): `repeated NodeTaint{key, value, effect NO_EXECUTE|NO_SCHEDULE|PREFER_NO_SCHEDULE}`, max 100. The same non-propagation and no-rollout caveat applies.
  - `resources` (3, required): `ResourcesSpec{platform (required), oneof size{preset}}`.
  - `gpu_cluster` (4): `GpuClusterSpec{id}`, type `computegpucluster`. "Nebius Compute GPUCluster ID that will be attached to node."
  - `network_interfaces` (5): max 1 item, `NON_EMPTY_DEFAULT`. Each `NetworkInterfaceTemplate` holds:
    - `public_ip_address` (`PublicIPAddress{}`, MEANINGFUL_EMPTY_VALUE: "Set to empty value, to enable it").
    - `subnet_id` (defaults to the control plane subnet; "Subnet should be located in the same network with control plane").
    - `security_groups[{id}]` (added 2026-06-16).
  - `cloud_init_user_data` (6): `(sensitive)`. "Should contain at least one SSH key."
  - `filesystems` (7): `repeated AttachedFilesystemSpec{attach_mode READ_ONLY|READ_WRITE (required), mount_tag (required), oneof type{existing_filesystem{id computefilesystem}}}`. "Can be used as a workaround, until CSI for Compute Disk and Filesystem will be available."
  - `boot_disk` (9): `DiskSpec`, `NON_EMPTY_DEFAULT`.
  - `service_account_id` (10): "credentials will be available on the nodes … also used to make requests to container registry. `resource.serviceaccount.issueAccessToken` permission is required."
  - `instance_metadata` (12): `InstanceMetadataTemplate{labels}`, "propagated into Compute Instance metadata". Added 2026-09-14.
  - `gpu_settings` (13): `GpuSettings{drivers_preset, dra}`, added 2025-02-20.
  - `preemptible` (15): `PreemptibleSpec{}` (MEANINGFUL_EMPTY_VALUE), added 2025-07-09.
  - `os` (16): added 2025-09-10. "Supported OS depend on the platform and Kubernetes version … run: nebius mk8s node-group get-compatibility-matrix --cluster-kubernetes-version VERSION --platform PLATFORM".
  - `nvlink` (17): `NVLinkSpec{oneof nvl_group{nvl_instance_group_id (IMMUTABLE, computenvlinstancegroup)}}`, added 2026-06-29.
  - `reservation_policy` (18): added 2026-01-26.
  - `local_disks` (19): added 2026-03-24.
  - `max_pods` (20): added 2026-06-04.
  - `oneof pricing_model` (21–23): added 2026-09-22.

  — Commits: [835ef38](https://github.com/nebius/api/commit/835ef38), [02c3f4e](https://github.com/nebius/api/commit/02c3f4e), [39a8b9b](https://github.com/nebius/api/commit/39a8b9b), [4f70f8a](https://github.com/nebius/api/commit/4f70f8a), [12dd5d6](https://github.com/nebius/api/commit/12dd5d6), [6778d2c](https://github.com/nebius/api/commit/6778d2c), [62f17e6](https://github.com/nebius/api/commit/62f17e6), [54acc86](https://github.com/nebius/api/commit/54acc86), [f61cc97](https://github.com/nebius/api/commit/f61cc97), [5a0adcf](https://github.com/nebius/api/commit/5a0adcf)
- `GpuSettings` ([node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)):
  - `drivers_preset` (1): "Identifier of the predefined set of drivers included in the ComputeImage … Leave empty for GPU nodes that do not have preinstalled drivers, including DRA-enabled node groups." Its `required` constraint was removed on 2026-09-01 ([commit 1e44269](https://github.com/nebius/api/commit/1e44269)).
  - `dra` (3, NON_EMPTY_DEFAULT): "Enables Dynamic Resource Allocation … disables the legacy NVIDIA device plugin … For GPU nodes attached to a Compute GPU cluster, advertises RDMA capability through the managed DRANet DaemonSet." Added 2026-09-14 ([commit f61cc97](https://github.com/nebius/api/commit/f61cc97)).
- `DiskSpec` ([instance_template.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/instance_template.proto)):
  - `oneof size{size_bytes | size_kibibytes | size_mebibytes | size_gibibytes}`, with a minimum of 64 GiB.
  - `block_size_bytes` (5).
  - `type` (6): `NETWORK_SSD`, `NETWORK_HDD`, `NETWORK_SSD_IO_M3`, `NETWORK_SSD_NON_REPLICATED`.
  - `disk_encryption` (11): `{type DISK_ENCRYPTION_UNSPECIFIED|DISK_ENCRYPTION_MANAGED}`, added 2026-09-03 ([commit 3ce26bc](https://github.com/nebius/api/commit/3ce26bc)).
- `max_pods`: 8..256, default 110. "MK8S uses this value to allocate a CIDR range for every node … node CIDR prefix is calculated as `32 - ceil(log2(2 * max_pods))`". With the default 110 that gives a /24. — [node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)
- `pricing_model` oneof ([node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)). "Default is on_demand."
  - `on_demand{}`: "A regular, non-preemptible VM".
  - `follows_spot_price{}`: "accepts the current spot price".
  - `spot_pricing_policy{id pricingpolicy}`: preempted when the spot price "exceeds the maximum price specified by the selected pricing policy".
- `ReservationPolicy{policy AUTO|FORBID|STRICT, reservation_ids[] computereservation}` is "copied as-is from NebiusAPI compute/v1/instance.proto", an "interface of the 'capacity block' (or 'capacity block group') mechanism" ([node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)):
  - `AUTO`: reservation IDs → any capacity block → PAYG.
  - `FORBID`: PAYG only.
  - `STRICT`: capacity blocks only, else fail.
- `LocalDisksSpec{oneof request{passthrough_group{requested bool}}, config LocalDisksSpecConfig (NON_EMPTY_DEFAULT) {oneof type{none | kubelet_ephemeral (default)}}}`. "strictly ephemeral: on node restart, all data is erased". — [node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)
- `NodeGroupDeploymentStrategy` (all three fields are documented under the [v1] defaults change):

  | Field | Rounding | Old default (2026-08-01) | New default (Q3 2026 onward) | Constraint / caveat |
  | --- | --- | --- | --- | --- |
  | `max_unavailable` (`PercentOrCount{percent 0..100 \| count ≥0}`) | Percent rounds down | 0 | 1 | Cannot be 0 if max_surge is 0 |
  | `max_surge` | Percent rounds up | 1 | 0 | Insufficient quota makes the update "hung because of quota exhausted error … visible in Operation.progress_data" |
  | `drain_timeout` (Duration ≥0) | — | 0 | 10m | "0 means no timeout … if PodDisruptionBudget doesn't allow evicting a pod, then NodeGroup update with node re-creation will hang" |

  For each new default: "for new clusters it will default to [it], node groups in existing clusters will be gradually migrated during Q3". The effective values are reported in `status.strategy`. — [node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto); defaults notice added [commit 5995ef7](https://github.com/nebius/api/commit/5995ef7) (2026-07-02)
- `NodeGroupAutoRepairSpec{conditions[NodeAutoRepairCondition{type (required), status ConditionStatus TRUE|FALSE|UNKNOWN, oneof trigger{timeout Duration | disabled bool ("disables the default auto-repair condition rules")}}]}`. — [node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)
- The official Soperator Terraform disables the default `Ready=FALSE` and `Ready=UNKNOWN` auto-repair rules, with the comment "Don't recreate the node if it's not ready for 5 minutes to avoid races with Soperator". — [soperator k8s_ng_workers_v2.tf](https://github.com/nebius/nebius-solution-library/blob/main/soperator/modules/k8s/k8s_ng_workers_v2.tf)
- `NodeGroupStatus` ([node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)):
  - `state`: PROVISIONING/RUNNING/DELETING.
  - `version`: `x.y.z-nebius-node.N`, e.g. "1.30.0-nebius-node.10".
  - Counts: `target_node_count` (fixed count or the autoscaler's decision), `node_count`, `outdated_node_count`, `ready_node_count`.
  - `events` (61).
  - `strategy` (62): the effective deployment strategy including defaults, added 2026-05-27 ([commit b0bf3c4](https://github.com/nebius/api/commit/b0bf3c4)).
  - `reconciling` (100).
- Template changes trigger rollout: "If not written opposite a NodeTemplate field update will cause NodeGroup roll-out according NodeGroupDeploymentStrategy." — [node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)
- The official Terraform GPU node group example ([k8s-training/main.tf](https://github.com/nebius/nebius-solution-library/blob/main/k8s-training/main.tf)):
  - Top level: `fixed_node_count`/`autoscaling{min_node_count,max_node_count}`, `parent_id = cluster.id`, `version`, `strategy`.
  - `template.resources{platform,preset}`, `boot_disk{size_gibibytes,type}`, `network_interfaces[{subnet_id, public_ip_address = {}}]`.
  - `filesystems[{attach_mode="READ_WRITE", mount_tag="data", existing_filesystem{id}}]`, `gpu_cluster`, `gpu_settings{drivers_preset}`.
  - `local_disks{config{kubelet_ephemeral=true}, passthrough_group{requested=true}}`, `cloud_init_user_data`.
  - Precondition: "GPU clustering requires an 8-GPU preset. Leave 'infiniband_fabric' empty for single-GPU presets."
- The GB300 example uses `nvlink = { nvl_instance_group_id = ... }` and `os = "ubuntu24.04"`. It requires `max_surge = { count = 0 }`: "so an update never requests a nineteenth rack node". — [k8s-training/gb300.tf](https://github.com/nebius/nebius-solution-library/blob/main/k8s-training/gb300.tf)
- **Discrepancy:** the official Terraform examples set attributes that are absent from the published v1 protos:
  - `template.preemptible = { on_preemption = "STOP", priority = 3 }`. The proto's `PreemptibleSpec` is empty.
  - `template.underlay_required = false`. No such proto field exists.

  — [k8s-training/main.tf](https://github.com/nebius/nebius-solution-library/blob/main/k8s-training/main.tf); [node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)

### Inferences
- `gpu_cluster` is how InfiniBand/RDMA fabric membership is requested; the Terraform variable is named `infiniband_fabric` and the DRA comment mentions RDMA. `nvlink` is the separate GB200/GB300-style NVL rack grouping.
- **Size cap:** a single node group is capped at 100 nodes (fixed or autoscaled max). Larger fleets need multiple node groups.
- **Labels and taints:** API changes to Kubernetes labels/taints are "create-time only" for existing nodes. This is a notable difference from providers that reconcile labels/taints in place.
- **Spot vs preemptible:** the newer `pricing_model` oneof overlaps the older `preemptible` flag, and the proto does not say how they interact.
- **Terraform-only attributes:** `underlay_required` and `preemptible.on_preemption`/`priority` suggest the Terraform provider schema is generated from a richer, possibly internal, proto than the one published. Do not assume those fields are settable via public gRPC.

### Gaps
- Default auto-repair rules (the exact conditions and timeouts) are not enumerated in the protos. The "5 minutes" figure comes from a comment in an example and is not normative.
- How `pricing_model` interacts with `preemptible`, and whether `on_preemption`/`priority` exist in the public API, is unverified.
- Platform/preset names (e.g. `gpu-h100-sxm`, `8gpu-128vcpu-1600gb`) are runtime values, not enumerated in the mk8s API. They were not verified here.

## 6. Long-running operations, errors, idempotency, concurrency, update semantics

### Takeaway
- **Operations:** all mutating mk8s RPCs return `nebius.common.v1.Operation`. It is complete when `status` (a `google.rpc.Status`) is set, and it is polled via `OperationService.Get` on `mk8s.api.nebius.cloud`.
- **Retries:** errors carry typed `ServiceError` details with a `retry_type`. Idempotency uses the `X-Idempotency-Key` header.
- **Updates:** `Update` is a full replace guarded by `resource_version`. Clearing fields requires the Nebius-specific `X-ResetMask` header; SDKs manage it automatically.
- **Concurrency:** no concurrent operations are allowed on one resource.

### Cited Findings
- `Operation` fields ([operation.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/operation.proto)):
  - `id`, `description`, `created_at`, `created_by`, `finished_at`.
  - `request` (Any).
  - `request_headers` (map, lower-case names, "For instance, `x-resetmask`").
  - `resource_id`.
  - `status` (google.rpc.Status, MEANINGFUL_EMPTY_VALUE).
  - `progress_tracker`.
  - `progress_data` (Any): "MAY be absent while the operation is running, MUST be absent after the operation has completed".
- `status.code` "must belong to an Operation-compatible subset of GRPC codes: OK, CANCELLED, PERMISSION_DENIED, RESOURCE_EXHAUSTED, FAILED_PRECONDITION, ABORTED, INTERNAL". On failure, `status.details` holds `ServiceError`s. — [operation.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/operation.proto)
- mk8s's `progress_data` type is `nebius.mk8s.v1.ProgressData{repeated Problem problems{stage, message}}`: "Problems encountered during the operation execution". — [progress_data.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/progress_data.proto)
- `ProgressTracker{description, started_at, estimated_finished_at, finished_at, work_done{total_tick_count, done_tick_count}, steps[]}`. — [progress_tracker.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/progress_tracker.proto)
- Completion and lifecycle rules: "An operation is considered complete when its `status` field is set. If the `status` field is `null`, the operation is still in progress." "Nebius AI Cloud does not support concurrent operations on the same resource … may result in an error, or the initial operation could be aborted in favor of the newer request." "Completed operations … may be deleted according to the retention policy." — [README](https://github.com/nebius/api/blob/main/README.md)
- `ServiceError{service, code, oneof details{...}, retry_type}` ([error.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/error.proto)):
  - Detail types: `BadRequest{violations[field,message,related_fields]}`, `BadResourceState`, `ResourceNotFound`, `ResourceAlreadyExists`, `OutOfRange`, `PermissionDenied`, `ResourceConflict`, `OperationAborted{operation_id, aborted_by_operation_id}`, `OperationConflict{conflicting_operation_id}`, `TooManyRequests`, `QuotaFailure{violations[quota,message,limit,requested]}`, `NotEnoughResources`, `InternalError{request_id, trace_id}`.
  - `retry_type` is `CALL` (just retry), `UNIT_OF_WORK` (redo the read-modify-write), or `NOTHING`.
- Idempotency: the "`X-Idempotency-Key` header … preferably a random-based UUID". It is ignored for Get/List. The gosdk defines `IdempotencyKeyHeader = "X-Idempotency-Key"`. — [README](https://github.com/nebius/api/blob/main/README.md); [gosdk conn/idempotency.go](https://github.com/nebius/gosdk/blob/main/conn/idempotency.go)
- Update semantics:
  - "the `Update` method is designed to perform a 'full-replace' of resource fields rather than a 'patch'". The server only changes fields that are non-default in the request or listed in the `X-ResetMask` header.
  - The mask syntax is comma-separated paths, with `*`, indices and grouping, e.g. `a, b.c, d.e.12, f.(j.h,i.j).k, l.*.m`.
  - Lists and maps are replaced as a whole when present.
  - "Nebius' Reset Mask is distinct from Google's Field Mask." "The Nebius SDK for all supported languages manages the `X-ResetMask` header automatically."

  — [README](https://github.com/nebius/api/blob/main/README.md)
- `resource_version`: "Service allows zero value or current". `GetClusterRequest`/`GetNodeGroupRequest` also accept `resource_version`. — [metadata.proto](https://github.com/nebius/api/blob/main/nebius/common/v1/metadata.proto); [cluster_service.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster_service.proto)
- gosdk `Operation.Wait`:
  - "polls the operation from the server until it's done".
  - `DefaultPollInterval = 1 * time.Second`, overridable via `PollInterval`.
  - "Consecutive retriable polling errors use exponential backoff with jitter".
  - "If the operation fails, `Wait` will return an error."

  — [gosdk operations/operation.go](https://github.com/nebius/gosdk/blob/main/operations/operation.go); [gosdk operations/options.go](https://github.com/nebius/gosdk/blob/main/operations/options.go); [gosdk README](https://github.com/nebius/gosdk/blob/main/README.md)
- The pysdk exposes operations as awaitables with `.wait()` (e.g. `sdk.whoami().wait()`). — [pysdk README](https://github.com/nebius/pysdk/blob/main/README.md)

### Inferences
- **Optimistic concurrency:** send `metadata.resource_version` = the last-read value on Update (or 0 to skip the check). A mismatch most likely yields a `ResourceConflict`/`FAILED_PRECONDITION` with `retry_type=UNIT_OF_WORK`. The exact code for a version mismatch is not documented.
- **Raw gRPC update pattern:** Get → modify → Update with the full spec, and add `X-ResetMask` paths only for fields being cleared to null/empty. Examples are removing `public_endpoint` (`spec.control_plane.endpoints.public_endpoint`) or `audit_logs`, since `MEANINGFUL_EMPTY_VALUE` messages can't be cleared by omission.
- **Failure visibility:** node group rollouts blocked by quota or PDBs surface as `ProgressData.problems` on the running operation rather than as a failed status.

### Gaps
- No mk8s-specific statement of which fields are mutable post-create exists beyond the `IMMUTABLE` annotations:
  - `control_plane.subnet_id`, `kube_network.service_cidrs` and `nvlink.nvl_instance_group_id` are `IMMUTABLE`.
  - Others are presumably mutable, some via rollout.
- No documented operation retention period for mk8s.

## 7. Authentication and authorization; kubeconfig credentials

### Takeaway
- **API calls:** every call carries `Authorization: Bearer <IAM access token>`. Users obtain the token via the CLI (`nebius iam get-access-token`, federated login). Service accounts sign an RS256 JWT with an authorized key and exchange it (RFC 8693) at `tokens.iam.api.nebius.cloud:443`.
- **kubectl access:** `nebius mk8s cluster get-credentials` writes a kubeconfig whose exec plugin calls `nebius mk8s v1 cluster get-token --format json`, so Kubernetes API auth is Nebius IAM tokens.
- **Authorization:** IAM is group-based, with the general roles auditor, viewer, editor and admin.

### Cited Findings
- "Nebius AI Cloud uses bearer token authentication. All requests must include an `Authorization: Bearer <IAM-access-token>` header." For users: "Run `nebius iam get-access-token`". — [README](https://github.com/nebius/api/blob/main/README.md)
- Service accounts "cannot be directly used for authentication":
  1. Generate a JWT with RS256 and claims `kid` (public key ID), `iss` = `sub` = service account ID, and a short `exp` (~5 min).
  2. Exchange it via gRPC `nebius.iam.v1.TokenExchangeService/Exchange` on `tokens.iam.api.nebius.cloud:443`, or HTTP `POST https://auth.eu.nebius.com:443/oauth2/token/exchange`.
     - `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`
     - `requested_token_type=urn:ietf:params:oauth:token-type:access_token`
     - `subject_token_type=urn:ietf:params:oauth:token-type:jwt`
  3. Refresh before `expires_in`.

  — [README](https://github.com/nebius/api/blob/main/README.md)
- Credentials file format used by the SDKs: `{"subject-credentials": {"alg":"RS256","private-key":"PKCS#8 PEM…","kid":"public-key-id","iss":"service-account-id","sub":"service-account-id"}}`. — [gosdk README](https://github.com/nebius/gosdk/blob/main/README.md)
- gosdk credential options:
  - `NoCredentials`, `IAMToken`, `CustomTokener`, `CustomAuthenticator`, `ServiceAccount`, `ServiceAccountReader`, `OneOfCredentials`, `PropagateAuthorizationHeader`.
  - The `auth` package also contains `federation*.go`, `federated_credentials.go`, `imds_tokener.go` and `impersonated_exchange.go`.
  - "The SDK does not automatically manage IAM token creation or refresh for user accounts". Service-account tokens are refreshed in the background.

  — [gosdk credentials.go](https://github.com/nebius/gosdk/blob/main/credentials.go); [gosdk auth/](https://github.com/nebius/gosdk/tree/main/auth); [gosdk README](https://github.com/nebius/gosdk/blob/main/README.md)
- pysdk init options:
  - `SDK(credentials=…)` (string token, `Bearer`, `EnvBearer("NEBIUS_IAM_TOKEN")`).
  - `SDK(config_reader=Config())` reuses the CLI profile, including a default `parent_id`.
  - `service_account_private_key_file_name` / `service_account_public_key_id` / `service_account_id`.
  - `credentials_file_name`.

  — [pysdk README](https://github.com/nebius/pysdk/blob/main/README.md)
- IAM services on `cpl.iam.api.nebius.cloud:443` include `AuthPublicKeyService`, `FederatedCredentialsService`, `FederationService`, `GroupService`, `GroupMembershipService`, `AccessPermitService`, `ServiceAccountService`, `StaticKeyService` and `SessionManagementService`. — [endpoints.md](https://github.com/nebius/api/blob/main/endpoints.md)
- CLI `nebius mk8s cluster get-credentials` takes the flags `--id`, `--external` (public endpoint), `--internal` (private endpoint), `--force` (overwrite an existing context), `--context-name` and `--kubeconfig`. The v1alpha1 variant `nebius mk8s v1alpha1 cluster get-credentials` is deprecated. — [CLI reference: get-credentials (via search snippet)](https://docs.nebius.com/cli/reference/mk8s/cluster/get-credentials)
- Typical usage in official examples: `nebius mk8s cluster get-credentials --id <cluster-id> --external`. — [nebius-solution-library](https://github.com/nebius/nebius-solution-library/blob/main/k8s-training/main.tf)
- The kubeconfig exec plugin used by the official Terraform: `api_version = "client.authentication.k8s.io/v1beta1"`, `command = "nebius"`, `args = ["mk8s", "v1", "cluster", "get-token", "--format", "json"]`. — [soperator/installations/example/terraform.tf](https://github.com/nebius/nebius-solution-library/blob/main/soperator/installations/example/terraform.tf)
- IAM model (via search snippet):
  - "Roles are assigned to groups". Each tenant has default groups mapped to the general roles: auditors, viewers, editors, admins.
  - "Viewer lets you view most types of resources … Editor lets you view and manage most types of resources. Admin lets you view and manage all types of resources."
  - The permissions table includes Managed Kubernetes clusters, but the per-role rows were not visible.

  — [Roles for Nebius AI Cloud groups (via search snippet)](https://docs.nebius.com/iam/authorization/roles)
- Node identity: `NodeTemplate.service_account_id` gives nodes SA credentials for the CLI/API and the container registry. Setting it requires `resource.serviceaccount.issueAccessToken`. — [node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto)

### Inferences
- **No static credentials:** the cluster API has no client-certificate or static-token option. Kubernetes authentication is delegated to Nebius IAM tokens through the CLI exec plugin, which needs the `nebius` CLI on the client. Only the CA cert comes from `status.control_plane.auth.cluster_ca_certificate`.
- **CI/automation:** service accounts plus the exec plugin, or a pre-fetched IAM token, cover automation use.
- **RBAC:** how Nebius IAM roles map to in-cluster Kubernetes RBAC groups (e.g. whether editor maps to `cluster-admin`) was not verifiable; see Gaps.

### Gaps
- The IAM-role → Kubernetes RBAC mapping and any mk8s-specific roles (e.g. a dedicated "k8s viewer") could not be confirmed, because docs.nebius.com was unreachable.
- No OIDC workload-identity (pod → Nebius SA) mechanism appears in the mk8s API. Only the node-level `service_account_id` exists.

## 8. Tooling (SDKs, CLI, Terraform), quotas/limits, and recent changes

### Takeaway
Official tooling:
- Go SDK `github.com/nebius/gosdk` and Python SDK `pip install nebius`, both generated from `nebius/api` and published the same day as protos.
- The `nebius` CLI (`nebius mk8s cluster|node-group ...`).
- The Terraform provider `nebius/nebius`, with `nebius_mk8s_v1_cluster`, `nebius_mk8s_v1_node_group` and matching data sources.

On quotas, the docs (via snippets) state a default of 2 clusters per region; nodes count against Compute and IP-allocation quotas; each group holds at most 100 nodes. Most API surface growth happened between 2025 and 2026.

### Cited Findings
- The README recommends the CLI, the Terraform provider, the Go SDK and the Python SDK. — [README](https://github.com/nebius/api/blob/main/README.md)
- gosdk: `go get github.com/nebius/gosdk`. Its latest commit is 2026-10-13, "Publish gosdk from 9f6a73b137", the same source hash as the proto publish. — [gosdk](https://github.com/nebius/gosdk); [gosdk README](https://github.com/nebius/gosdk/blob/main/README.md)
- pysdk: `pip install nebius`. Its async-first `SDK` documents migration notes 0.2→0.3→0.4→0.5. — [pysdk README](https://github.com/nebius/pysdk/blob/main/README.md)
- CLI commands seen in sources:
  - `nebius mk8s cluster get-credentials`
  - `nebius mk8s v1 cluster get-token --format json`
  - `nebius mk8s node-group get-compatibility-matrix --cluster-kubernetes-version VERSION --platform PLATFORM`
  - The CLI also keeps `nebius mk8s v1alpha1 ...` (deprecated).

  — [node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto); [CLI reference (via search snippet)](https://docs.nebius.com/cli/reference/mk8s/cluster)
- CLI release notes (via search snippet):
  - 2026-09-02 added a boot disk encryption type option to `nebius mk8s node-group create`/`update`.
  - 2026-06-25 added an NVLink instance group ID flag to node group create.

  — [CLI release notes (via search snippet)](https://docs.nebius.com/cli/release-notes)
- Terraform:
  - The provider source is `nebius/nebius`.
  - Resources are `nebius_mk8s_v1_cluster` and `nebius_mk8s_v1_node_group`.
  - Metadata is flattened (`parent_id`, `name`, `labels` at the top level), and spec fields are top-level attributes (`control_plane`, `kube_network`, `template`, `strategy`, `autoscaling`, `fixed_node_count`, `auto_repair`).
  - A data source `nebius_mk8s_v1_node_group` is documented.

  — [k8s-training/main.tf](https://github.com/nebius/nebius-solution-library/blob/main/k8s-training/main.tf); [soperator k8s_ng_workers_v2.tf](https://github.com/nebius/nebius-solution-library/blob/main/soperator/modules/k8s/k8s_ng_workers_v2.tf); [TF data source mk8s_v1_node_group (via search snippet)](https://docs.nebius.com/terraform-provider/reference/data-sources/mk8s_v1_node_group)
- Quotas/limits (via search snippets of the current docs):
  - "Clusters per region | 2" (default quota).
  - Nodes use Compute VM quotas.
  - Node count is bounded by the "Total number of allocations" quota (default 1000): about 498 nodes with private IPs only and no HA, or about 331 with public and private IPs plus HA.
  - Each node with a public IP needs 1 /32 public IP allocation.
  - Max 100 nodes per node group.
  - Quotas are regional and set per project; admins change them in Administration → Limits.

  — [Kubernetes quotas & limits (via search snippet)](https://docs.nebius.com/kubernetes/resources/quotas-limits); [Clusters manage (via search snippet)](https://docs.nebius.com/kubernetes/clusters/manage); [Quotas overview (via search snippet)](https://docs.nebius.com/overview/quotas)
- **Conflict:** a legacy page at `nebius.ai/docs/managed-kubernetes/concepts/limits` gives different limits ("8 clusters per cloud"). It belongs to the older nebius.ai platform and should not be used for the current API. — [legacy nebius.ai limits](https://nebius.ai/docs/managed-kubernetes/concepts/limits); compare [current docs (via search snippet)](https://docs.nebius.com/kubernetes/resources/quotas-limits)
- The 100-node cap is also enforced in the API (`fixed_node_count` and autoscaling min/max `lte: 100`). Other API-enforced limits:
  - ≤100 Kubernetes labels and ≤100 taints per template
  - ≤8 public-endpoint CIDRs
  - 1 service CIDR
  - 1 network interface

  — [node_group.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/node_group.proto); [cluster.proto](https://github.com/nebius/api/blob/main/nebius/mk8s/v1/cluster.proto)
- Timeline of mk8s v1 API changes, from git history of `nebius/api`:

  | Date | Change | Commit |
  | --- | --- | --- |
  | 2024-09-17 | v1 + v1alpha1 published, including `Upgrade`, `drain_timeout`, `NETWORK_SSD_IO_M3` | [d478ba0](https://github.com/nebius/api/commit/d478ba0) |
  | 2024-09-19 | `service_account_id` | — |
  | 2025-02-20 | `gpu_settings.drivers_preset` | [02c3f4e](https://github.com/nebius/api/commit/02c3f4e) |
  | 2025-07-09 | `preemptible` | [39a8b9b](https://github.com/nebius/api/commit/39a8b9b) |
  | 2025-09-10 | `template.os` | [4f70f8a](https://github.com/nebius/api/commit/4f70f8a) |
  | 2025-09-11 | `ListControlPlaneVersions` | [136a7e8](https://github.com/nebius/api/commit/136a7e8) |
  | 2025-09-25 | status `events` | [b582af3](https://github.com/nebius/api/commit/b582af3) |
  | 2025-11-10 | v1alpha1 marked deprecated | — |
  | 2025-11-11 | `auto_repair` + `GetCompatibilityMatrix` | [03c83a7](https://github.com/nebius/api/commit/03c83a7) |
  | 2026-01-26 | `audit_logs` + `reservation_policy` | [835ef38](https://github.com/nebius/api/commit/835ef38) |
  | 2026-03-16 | `karpenter` | [4bcb49f](https://github.com/nebius/api/commit/4bcb49f) |
  | 2026-03-24 | `local_disks` | [6778d2c](https://github.com/nebius/api/commit/6778d2c) |
  | 2026-05-04 | `restricted` control-plane versions | [2a50a88](https://github.com/nebius/api/commit/2a50a88) |
  | 2026-05-27 | `status.strategy` | [b0bf3c4](https://github.com/nebius/api/commit/b0bf3c4) |
  | 2026-06-04 | `max_pods` | [62f17e6](https://github.com/nebius/api/commit/62f17e6) |
  | 2026-06-16 | `allowed_cidrs` + `security_groups` | [5a0adcf](https://github.com/nebius/api/commit/5a0adcf) |
  | 2026-06-29 | `nvlink` | [12dd5d6](https://github.com/nebius/api/commit/12dd5d6) |
  | 2026-07-02 | Q3-2026 strategy default changes announced in comments | [5995ef7](https://github.com/nebius/api/commit/5995ef7) |
  | 2026-07-08 | typed `(nid)` ID annotations | [bbb05e4](https://github.com/nebius/api/commit/bbb05e4) |
  | 2026-09-01 | `drivers_preset` no longer required | [1e44269](https://github.com/nebius/api/commit/1e44269) |
  | 2026-09-03 | `disk_encryption` | [3ce26bc](https://github.com/nebius/api/commit/3ce26bc) |
  | 2026-09-14 | `gpu_settings.dra` + `instance_metadata` | [f61cc97](https://github.com/nebius/api/commit/f61cc97) |
  | 2026-09-16 | `PreflightCheck` | [1396e2a](https://github.com/nebius/api/commit/1396e2a) |
  | 2026-09-22 | `pricing_model` (`on_demand` / `follows_spot_price` / `spot_pricing_policy`) | [54acc86](https://github.com/nebius/api/commit/54acc86) |

  — [nebius/api history](https://github.com/nebius/api/commits/main/nebius/mk8s/v1)
- Nebius's Q3 2026 update says Managed Kubernetes "achieved CNCF Kubernetes conformance certification". — [Nebius blog: Q3 2026 updates (via search snippet)](https://nebius.com/blog/posts/compute-cloud-platform-q3-2026)

### Inferences
- **Pace of change:** the API is evolving fast, with new node group fields roughly monthly in 2026. A field-by-field comparison should pin to a proto commit. Terraform/CLI may expose attributes before, or beyond, what the public protos show (see the `underlay_required` discrepancy in section 5).
- **Quota for comparison:** the default of 2 clusters per region is low and is a quota, not a hard limit; it can be raised via support or Limits.

### Gaps
- The current Terraform provider version, and the full list of mk8s data sources, could not be retrieved: the registry was blocked. A `nebius_mk8s_v1_cluster` data source very likely exists but was not verified.
- No Java/TypeScript official SDK was confirmed. The protos declare `java_package` but no Java SDK repo was checked.
- The full quota table could not be read because the page was unreachable, so no per-project node-group count limit was found. Rate limits (TooManyRequests thresholds) are also undocumented.
