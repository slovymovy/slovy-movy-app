# CoreWeave Kubernetes Service (CKS) — public API design, resource model, node pools, auth and tooling (state as of October 2026)

> Method note for the report writer. Most of the authoritative detail below comes from two primary sources:
> (1) the **CoreWeave-published Protobuf API definitions** (Buf modules `buf.build/coreweave/cks` and `buf.build/coreweave/networking`). I decoded the full descriptors, including `google.api.http`, `google.api.field_behavior` and `buf.validate` options, from the Buf-generated Go SDKs at their latest commits: **cks `77e2aa82efe3`, 2026-06-30** and **networking `bfceb1d95ae7`, 2026-09-03**.
> (2) The **official Terraform provider source**, `github.com/coreweave/terraform-provider-coreweave`, at HEAD on 2026-10-13 (latest release v0.24.0, 2026-09-11).
> I could not fetch `docs.coreweave.com` directly from this environment because egress to it was rejected. Every fact attributed to the docs therefore comes through search-engine extraction of those pages. Treat docs-only facts as **slightly lower confidence** than proto or provider facts.

## 1. API style, transport, base URL, versions and where definitions are published

### Takeaway
CKS is a **Protobuf-first API served over Connect RPC/gRPC**, with Google-style HTTP/JSON transcoding (`google.api.http`) on `https://api.coreweave.com`. The **production version is `coreweave.cks.v1beta1`**, with REST paths under `/v1beta1/cks/clusters`. A redesigned, AIP-style **`v1beta2`** package is already published in the Buf module, but I found no documentation for it and the official tooling does not use it. VPCs live in a separate `coreweave.networking.v1beta1` API. Node pools are **not** in the cloud API: they are Kubernetes CRDs (see §3).

### Cited Findings
- **Base URL and auth scheme.** The CKS API host is `https://api.coreweave.com`, with Bearer-token auth. `GET /v1beta1/cks/clusters` lists clusters and `POST` creates one. — [CKS API docs](https://docs.coreweave.com/products/cks/reference/cks-api); [Create cluster](https://docs.coreweave.com/products/cks/reference/cks-api/clusterservice/create-cluster)
- **gRPC exposure.** The docs say the CKS API is also exposed as a gRPC service: `ClusterService` in package `coreweave.cks.v1beta1`, published as the public Buf module `buf.build/coreweave/cks`. — [CKS API docs](https://docs.coreweave.com/products/cks/reference/cks-api)
- **v1beta1 service definition.** Proto file `coreweave/cks/v1beta1/clusters.proto` defines `service ClusterService` with these HTTP bindings (decoded from the descriptor) — [buf.build/coreweave/cks @77e2aa82efe3](https://buf.build/coreweave/cks):
  - `ListClusters`: `GET /v1beta1/cks/clusters`
  - `GetCluster`: `GET /v1beta1/cks/clusters/{id}`
  - `CreateCluster`: `POST /v1beta1/cks/clusters`, body `*`
  - `UpdateCluster`: `PATCH /v1beta1/cks/clusters/{id}`, body `*`
  - `DeleteCluster`: `DELETE /v1beta1/cks/clusters/{id}`
- **Networking service definition.** `coreweave/networking/v1beta1/vpc.proto` defines `service VPCService` — [buf.build/coreweave/networking @bfceb1d95ae7](https://buf.build/coreweave/networking):
  - `ListVPCs`: `GET /v1beta1/networking/vpcs`
  - `GetVPC`: `GET /v1beta1/networking/vpcs/{id}`
  - `CreateVPC`: `POST /v1beta1/networking/vpcs`
  - `UpdateVPC`: `PATCH /v1beta1/networking/vpcs/{id}`
  - `DeleteVPC`: `DELETE /v1beta1/networking/vpcs/{id}`
- **Transport used by official tooling.**
  - The Terraform provider calls CKS and VPC through **Connect RPC** clients: `connectrpc.com/connect/v2`, `cksv1beta1connect.NewClusterServiceClient` and `networkingv1beta1connect.NewVPCServiceClient`.
  - These are generated from `buf.build/gen/go/coreweave/cks/connectrpc/go/v2` and `buf.build/gen/go/coreweave/networking/connectrpc/go/v2`.
  - The provider's default endpoint is `https://api.coreweave.com/`, overridable with `COREWEAVE_API_ENDPOINT`.
  - Sources: [provider client.go](https://github.com/coreweave/terraform-provider-coreweave/blob/main/coreweave/client.go); [provider go.mod](https://github.com/coreweave/terraform-provider-coreweave/blob/main/go.mod); [provider index docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/index.md)
- **Other Buf modules on the same API host.** The same provider also consumes these CoreWeave Buf modules, which shows the platform-wide pattern of "one Buf module per product, versioned package":
  - `container-registry-api` (`coreweave.registry.v1alpha1`)
  - `cwobject` (`cwobject.v1`)
  - `inference` (`coreweave.inference.v1alpha1`)
  - `sandbox` (`coreweave.sandbox.v1`)
  - `workload-federation` (`coreweave.workload_federation.control_plane.v1beta1`)
  - Source: [provider go.mod](https://github.com/coreweave/terraform-provider-coreweave/blob/main/go.mod)
- **Protobuf conventions.**
  - Field semantics are annotated with `google.api.field_behavior` (`REQUIRED`, `OPTIONAL`, `OUTPUT_ONLY`, `IMMUTABLE`, `UNORDERED_LIST`).
  - Validation uses `buf.validate` (protovalidate) rules.
  - Example: cluster `name` has `min_len:1 max_len:30 pattern:"^[a-z0-9]([-a-z0-9]*[a-z0-9])?$"`.
  - `PortRange` has the CEL rule `end_gte_start: this.end >= this.start`.
  - Source: [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
- **v1beta2 package (published but apparently unreleased).**
  - The latest cks module also contains `coreweave/cks/v1beta2/{clusters,apiserver,network}.proto`.
  - Its `ClusterService` is resource-name based:
    - `GetCluster`: `GET /v1beta2/cks/{name=*}`
    - `ListClusters`: `GET /v1beta2/cks/clusters`
    - `CreateCluster`: `POST /v1beta2/cks/clusters`, body `cluster`, `method_signature "cluster,cluster_id"`
    - `UpdateCluster`: `PATCH /v1beta2/cks/{cluster.name=*}`, body `cluster`
    - `DeleteCluster`: `DELETE /v1beta2/cks/{name=*}`, returns `Empty`
  - Messages carry `google.api.resource` annotations (`type:"coreweave.cks.v1beta2.Cluster" pattern:"{cluster}"`).
  - Source: [buf.build/coreweave/cks @77e2aa82efe3](https://buf.build/coreweave/cks)
- **Module revisions.**
  - The provider pins cks module commit `be22b90e1aa6` (2026-03-26) and networking `a637e7777165` (2026-01-21).
  - The latest published revisions are cks `77e2aa82efe3` (2026-06-30) and networking `bfceb1d95ae7` (2026-09-03).
  - Sources: [provider go.mod](https://github.com/coreweave/terraform-provider-coreweave/blob/main/go.mod); [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
- **Third-party OpenAPI mirror.** apis.io lists a "CoreWeave Clusters API" OpenAPI rendering in which create returns `201`. This is a third-party mirror and is unverified against CoreWeave. — [apis.io](https://apis.io/apis/coreweave/coreweave-clusters-api/)
- **Required-field inconsistency in the docs.** One page lists `name`, `zone`, `vpcId`, `version`, `network.podCidrName`, `network.serviceCidrName` and `network.internalLbCidrNames` as required. Another lists more, including `public`, `oidc.issuerUrl`, `oidc.clientId`, `authzWebhook.server` and `authnWebhook.server`. — [CKS API docs](https://docs.coreweave.com/products/cks/reference/cks-api); [Create cluster](https://docs.coreweave.com/products/cks/reference/cks-api/clusterservice/create-cluster)
  - The proto marks `public`, `oidc`, `authz_webhook` and `authn_webhook` as `OPTIONAL` at cluster level. `issuer_url`, `client_id` and `server` are `REQUIRED` only inside those sub-messages when present. — [buf.build/coreweave/cks](https://buf.build/coreweave/cks)

### Inferences
- **Transport.** Because the API is defined in proto with `google.api.http` transcoding, a client can use any of three transports against the same host: Connect (HTTP/1.1 or HTTP/2, JSON or binary), gRPC, or plain REST/JSON. JSON field names are lowerCamelCase proto JSON names (`vpcId`, `podCidrName`, `internalLbCidrNames`, `apiServerEndpoint`).
- **Design shift in v1beta2.** v1beta2 brings CKS closer to Google AIP conventions:
  - canonical resource `name` (a UUID), plus `display_name`
  - client-chosen `cluster_id`
  - `request_id` for idempotency
  - a CEL `filter` on List
  - k8s-style `conditions`
  - `*Metadata` messages, which suggest future long-running-operation semantics
  - networking settings (`vpc_id`, `public_access`) moved into `NetworkConfig`, and apiserver auth into `APIServerConfig`
  
  This looks like an in-progress migration path. As of 2026-10 it is not used by the official provider, and I did not find it in the public docs.

### Gaps
- I found no official public OpenAPI or Swagger file. The docs render per-RPC reference pages, but I could not fetch them to see whether they embed an OpenAPI spec.
- I could not confirm whether `v1beta2` endpoints are live on `api.coreweave.com` or only defined in the module. I found no release note, and no docs page announced it.
- I found no published SLA or stability or deprecation policy for `v1beta1` vs `v1beta2`.

## 2. Resource model: Cluster object and VPC / networking API

### Takeaway
A v1beta1 `Cluster` is a flat object. Its key fields are:
- `id`, `name`, `zone`, `vpc_id`, `public`, `version`
- `network` (named VPC-prefix references, not raw CIDRs)
- `oidc`, `authn_webhook`, `authz_webhook`, `audit_policy`, `kubelet`, `additional_server_sans`, `tailscale`
- output-only `status`, `api_server_endpoint`, timestamps and `is_upgradeable`

Clusters depend on a pre-created zonal **VPC**. The VPC owns named `vpc_prefixes` (pod, service and internal-LB CIDRs) and `host_prefixes` (node addressing). The cluster refers to those prefixes **by name**.

### Cited Findings

**`coreweave.cks.v1beta1.Cluster`** (field numbers are given for exact comparison) — [buf.build/coreweave/cks @77e2aa82efe3](https://buf.build/coreweave/cks)

| # | Field (proto / JSON) | Type | Behavior / validation | Description (from proto comments) |
|---|---|---|---|---|
| 1 | `id` | string | OUTPUT_ONLY, IMMUTABLE | Unique identifier; server-assigned. |
| 2 | `name` | string | REQUIRED, IMMUTABLE; 1–30 chars, `^[a-z0-9]([-a-z0-9]*[a-z0-9])?$` | Cluster name. |
| 3 | `zone` | string | REQUIRED, IMMUTABLE | Availability Zone, e.g. `US-EAST-04A`. |
| 4 | `vpc_id` / `vpcId` | string | REQUIRED, IMMUTABLE | VPC in the same AZ. |
| 5 | `public` | bool | OPTIONAL | Whether the api-server is reachable from the internet. |
| 6 | `version` | string | REQUIRED | Minor version, e.g. `"v1.32"`; CKS applies patch versions automatically. |
| 7 | `network` | `ClusterNetworkConfig` | REQUIRED | Overlay network config. |
| 8 | `oidc` | `OIDCConfig` | OPTIONAL | OIDC authentication to the api-server. |
| 9 | `authz_webhook` | `AuthWebhookConfig` | OPTIONAL | Authorization webhook. |
| 10 | `authn_webhook` | `AuthWebhookConfig` | OPTIONAL | Authentication webhook. |
| 11 | `audit_policy` | string | OPTIONAL | Base64-encoded JSON/YAML audit policy. |
| 12 | `status` | enum `Cluster.Status` | OUTPUT_ONLY | Current status. |
| 13 | `api_server_endpoint` | string | OUTPUT_ONLY | Api-server endpoint. |
| 14/15 | `created_at`, `updated_at` | Timestamp | OUTPUT_ONLY | Creation and last-update times. |
| 17 | `is_upgradeable` / `isUpgradeable` | bool | OUTPUT_ONLY | "Whether self serve upgrades are supported for the cluster". |
| 18 | `disable_oidc_info_publication` | bool | OPTIONAL | Stops publishing `/.well-known/openid-configuration` and `/openid/v1/jwks` to `oidc.cks.coreweave.com/id/<cluster-id>`, which is otherwise a public OIDC provider. |
| 19 | `kubelet` | `google.protobuf.Struct` | OPTIONAL | Selective KubeletConfiguration overrides for all nodes, as JSON. A node reboot is required; unknown keys are stored but ignored. |
| 20 | `additional_server_sans` | repeated string | OPTIONAL; max 10, unique | Extra SANs on the K8s API certificate. |
| 21 | `tailscale` | `Tailscale` | OPTIONAL | `client_id` (≤255 chars) and OUTPUT_ONLY `tailnet_domain`. |

- **Status enum.** `Cluster.Status` values are:
  - `STATUS_UNSPECIFIED`=0, `STATUS_CREATING`=1, `STATUS_UPDATING`=2, `STATUS_RUNNING`=3
  - `STATUS_DELETING`=4, `STATUS_DELETED`=5, `STATUS_ERROR`=6, `STATUS_FAILED`=7
  - `STATUS_UPGRADING`=8, `STATUS_UPGRADE_FAILED`=9
  - `STATUS_PREEMPTED`=10: "Cluster creation was preempted by another operation. Only DeleteCluster is allowed on a preempted cluster." This comment was added between the 2026-03-26 and 2026-06-30 module revisions.
  - Source: [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
- **`ClusterNetworkConfig`** — [buf.build/coreweave/cks](https://buf.build/coreweave/cks):
  - `pod_cidr_name`: REQUIRED, IMMUTABLE.
  - `service_cidr_name`: REQUIRED, IMMUTABLE.
  - `internal_lb_cidr_names`: repeated, REQUIRED, min 1, unique, **append-only**. Internal LBs are "reachable within the VPC but not accessible from the internet".
  - `service_node_port_range` (`PortRange{start,end}`, 1–65535, end ≥ start): OPTIONAL.
  - IPv6 dual-stack fields `pod_cidr_name_v6`, `service_cidr_name_v6` and `internal_lb_cidr_names_v6`: "If any IPv6 field is set, then ALL IPv6 fields must be set". The `_v6` CIDR names are IMMUTABLE.
  - Every `*_cidr_name` value is the **name** of a `vpc_prefixes` entry that must already exist in the cluster's VPC.
- **`OIDCConfig`** — [buf.build/coreweave/cks](https://buf.build/coreweave/cks):
  - `issuer_url`: REQUIRED, must be a URI.
  - `client_id`: REQUIRED.
  - `username_claim`, `username_prefix`, `groups_claim`, `groups_prefix`.
  - `ca`: base64 PEM.
  - `required_claim`.
  - `signing_algorithms`: repeated enum `SigningAlgorithm`. **v1beta1 supports only `SIGNING_ALGORITHM_RS256`.**
  - `admin_group_binding`: "optional oidc group name to bind to cluster-admin RBAC".
- **`AuthWebhookConfig`.** Fields are `server` (REQUIRED URI) and `ca` (base64 PEM). — [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
- **Create-only and update-only request fields.** `CreateClusterRequest` additionally has `shared_storage_cluster_id` (field 12), described as "Cluster ID (not name) of the cluster with shared storage". The Terraform docs say it "Must be enabled by CoreWeave support". — [buf.build/coreweave/cks](https://buf.build/coreweave/cks); [TF cks_cluster docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/resources/cks_cluster.md)
- **Terraform-only read-only attribute.** The provider exposes `service_account_oidc_issuer_url`, "The URL of the OIDC issuer for the cluster's service account tokens… corresponds to the `--service-account-issuer` flag". It was added in provider v0.4.0 (2025-09-23). This field is not in the v1beta1 `Cluster` proto, so the provider derives it. — [TF cks_cluster docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/resources/cks_cluster.md); [CHANGELOG](https://github.com/coreweave/terraform-provider-coreweave/blob/main/CHANGELOG.md)
- **v1beta2 `Cluster` (not yet documented)** — [buf.build/coreweave/cks](https://buf.build/coreweave/cks):
  - `name`: OUTPUT_ONLY canonical name, a UUID.
  - `display_name`: REQUIRED, IMMUTABLE, ≤30.
  - `create_time`, `update_time`.
  - `zone`, `version`.
  - `api_server` (`APIServerConfig`):
    - `oidc`, `authz_webhook`, `authn_webhook`
    - `audit_policy` as `bytes`
    - `node_port_range`
    - OUTPUT_ONLY `api_server_endpoint`
  - `network` (`NetworkConfig`):
    - `vpc_id`
    - `public_access`
    - `pod_cidr_name` and `service_cidr_name`, both **repeated**, which suggests dual-stack in one list
    - `internal_lb_cidr_names`
  - `disable_pod_log_ingestion`: new; disables ingestion of non-CoreWeave-namespace pod logs into CKS tooling.
  - `status`: enum `CREATING`/`UPDATING`/`UPGRADING`/`RUNNING`/`DELETING`/`DELETED`/`FAILED`.
  - `conditions[]` with `type`, `reason`, `message`, `last_update_time`.
  - `upgradeable`.
  - v1beta2 OIDC `signing_algorithms` expands to RS256/384/512, ES256/384/512 and PS256/384/512.
  - In v1beta2, webhook `ca` and OIDC `ca` become `bytes`.
- **`coreweave.networking.v1beta1.VPC`** — [buf.build/coreweave/networking @bfceb1d95ae7](https://buf.build/coreweave/networking):
  - `id`: OUTPUT_ONLY.
  - `name`: REQUIRED, IMMUTABLE, ≤30, DNS-label pattern.
  - `status`: enum `STATUS_CREATING`/`UPDATING`/`READY`/`DELETING`.
  - `zone`: REQUIRED, IMMUTABLE.
  - `vpc_prefixes`: repeated `Prefix`.
  - `created_at`, `updated_at`.
  - `host_prefix`: string, deprecated, IMMUTABLE.
  - `host_prefixes`: repeated `HostPrefix`, IMMUTABLE.
  - `ingress{disable_public_services}`.
  - `egress{disable_public_access}`.
  - `dhcp{dns{servers[]}}`, where server IPs must be valid and unique.
  - CEL rule `host_prefix_mutual_exclusion`: "only one of host_prefix (deprecated) or host_prefixes may be set". If neither is set, "per-Zone defaults will apply".
- **`Prefix`** — [buf.build/coreweave/networking](https://buf.build/coreweave/networking):
  - `name`: REQUIRED, IMMUTABLE, ≤30, pattern `^[a-zA-Z0-9-_ ]+$`, so **spaces are allowed**, e.g. `"pod cidr"`.
  - `value`: REQUIRED, IMMUTABLE, `ip_with_prefixlen`.
  - `created_at`, `updated_at`.
  - `status`: OUTPUT_ONLY enum `STATUS_AVAILABLE`=2, `STATUS_PENDING_ALLOCATION`=3, `STATUS_ALLOCATED`=4.
- **`HostPrefix`** — [buf.build/coreweave/networking](https://buf.build/coreweave/networking):
  - `name`: REQUIRED.
  - `type`: enum `PRIMARY`=1, `ROUTED`=2, `ATTACHED`=3.
  - `prefixes[]`: IPv4 or IPv6 CIDRs.
  - `ipam` (`IPAddressManagementPolicy`):
    - `prefix_length`: the per-node allocation size.
    - `gateway_address_policy`: `EUI64`/`FIRST_IP`/`LAST_IP`. EUI64 requires `prefix_length` 64.
  - CEL rules: `ipam` must **not** be set for `PRIMARY`, and **is required** for secondary (`ROUTED`/`ATTACHED`) prefixes. A further rule restricts `gateway_address_policy` to attached-only.
- **VPC update scope.** `UpdateVPCRequest` can change only `vpc_prefixes`, `ingress`, `egress` and `dhcp`, plus `update_mask`. Name, zone and host prefixes are immutable. — [buf.build/coreweave/networking](https://buf.build/coreweave/networking)
- **Host-prefix guidance.**
  - For SUNK, use an IPv4 host prefix "no smaller than your Zone's default host prefix" so node addresses encode physical location.
  - NVL72 rack-level instances need a primary host prefix no smaller than the Zone default, or IMEX address mapping breaks.
  - Provider v0.15.1 (2026-06-29) scoped a former `/18` host_prefix requirement to SUNK only.
  - Sources: [TF networking_vpc docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/resources/networking_vpc.md); [CHANGELOG](https://github.com/coreweave/terraform-provider-coreweave/blob/main/CHANGELOG.md)
- **Canonical Terraform example.**
  - VPC `zone = "US-EAST-04A"` with `host_prefixes=[{name="primary", type="PRIMARY", prefixes=["10.16.192.0/18"]}]`.
  - `vpc_prefixes`: `"pod cidr"=10.0.0.0/13`, `"service cidr"=10.16.0.0/22`, `"internal lb cidr"=10.32.4.0/22`.
  - Cluster `version = "v1.35"`, `public = false`, `pod_cidr_name = "pod cidr"`, and so on.
  - Source: [TF cks_cluster docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/resources/cks_cluster.md)

### Inferences
- **Indirect CIDR model.** CKS uses an indirection that some other clouds do not: the cluster stores CIDR **names**, the VPC stores the actual values, and a `Prefix.status` (`ALLOCATED`) tracks consumption. For a field-by-field comparison, the comparable "pod CIDR" lives on the VPC object, not the cluster object.
- **No location hierarchy.** There is no project, folder or parent field on any resource. Scoping is implicit in the bearer token's organization, and location is a single `zone` string. VPCs and clusters are **zonal**, and a cluster must be in the same AZ as its VPC.

### Gaps
- I did not find a list or API of valid `zone` values. Zones are documented in an availability matrix that I could not fetch.
- I could not confirm the exact semantic difference between `STATUS_ERROR` and `STATUS_FAILED`, or when `STATUS_UPGRADE_FAILED` is set.
- I found no API to list supported Kubernetes versions; v1beta1 has no `ListVersions` RPC.

## 3. Node pools: Kubernetes-native `NodePool` CRD (`compute.coreweave.com/v1alpha1`), node lifecycle labels

### Takeaway
Node pools are **not** managed through the CoreWeave cloud API, Buf modules or the Terraform `coreweave` provider. They are a **Kubernetes CRD, `NodePool` in `compute.coreweave.com/v1alpha1`**, which you apply inside the CKS cluster with kubectl or the Terraform `kubernetes_manifest` resource. CoreWeave's controllers reconcile it against org quota and capacity.

Node lifecycle and health are surfaced as labels and annotations on core `Node` objects: `node.coreweave.cloud/state`, `/pending-state`, `/reserved`, `/cordonReason` and others.

### Cited Findings
- **CRD identity and basic shape.** `NodePool` is in API group `compute.coreweave.com/v1alpha1`. The spec includes `computeClass`, `autoscaling`, `instanceType`, `minNodes`, `maxNodes`, `targetNodes`, and optional node labels, annotations and taints. `targetNodes` is an integer (min 0) giving the desired node count. — [Node Pool reference](https://docs.coreweave.com/products/cks/reference/node-pool); [Create a Node Pool](https://docs.coreweave.com/products/cks/nodes/create)
- **No Terraform resource.** CoreWeave's own reference-architecture repo states: "CKS NodePool via Kubernetes provider (CoreWeave has no Terraform nodepool resource). CRD: compute.coreweave.com/v1alpha1 NodePool". It uses `kubernetes_manifest` with:
  - `spec.autoscaling`, `instanceType`, `maxNodes`, `minNodes`, `targetNodes`
  - optional `nodeLabels` (map), `nodeAnnotations` (map) and `nodeTaints` (list of `{key,value,effect}`)
  - Source: [reference-architecture terraform/modules/nodepool/main.tf](https://github.com/coreweave/reference-architecture/blob/main/terraform/modules/nodepool/main.tf)
  - The provider's resource list contains no node-pool resource. Its resources are `cks_cluster`, `networking_vpc`, container registry, inference, object storage, `sandbox_managed_runner` and `workload_federation_oidc_config`. — [provider docs/resources](https://github.com/coreweave/terraform-provider-coreweave/tree/main/docs/resources)
- **Minimal manifest example** from CoreWeave's own repo — [reference-architecture cpu-nodepool.yaml](https://github.com/coreweave/reference-architecture/blob/main/secrets-management/cloud-kms/gcp-kms/manifests/cpu-nodepool.yaml):

  ```yaml
  apiVersion: compute.coreweave.com/v1alpha1
  kind: NodePool
  metadata: {name: cpu}
  spec:
    instanceType: cd-gp-i64-erapids
    targetNodes: 1
    nodeConfigurationUpdateStrategy: {type: OnSpecUpdate}
  ```

  `NodePool` is created without a namespace, so it appears to be cluster-scoped. GPU instance types follow the form `gd-8xh100ib-i128`. — [reference-architecture nodepool variables.tf](https://github.com/coreweave/reference-architecture/blob/main/terraform/modules/nodepool/main.tf)
- **Spec fields** documented in the reference and how-to pages:
  - `computeClass`: enum, default `default`, used for Reserved and On-Demand instances. `spot` creates a Spot pool, per the Spot docs. The reference lists only `default`, so the docs conflict here. — [Node Pool reference](https://docs.coreweave.com/products/cks/reference/node-pool); [Spot Node Pools](https://docs.coreweave.com/platform/capacity-plans/spot-node-pools); [Spot release note, 2026-03-10](https://docs.coreweave.com/release-notes/spot-node-pools)
  - `instanceType`: required, **immutable**. — [Node Pool reference](https://docs.coreweave.com/products/cks/reference/node-pool)
  - `targetNodes` and `targetRacks` are mutually exclusive; exactly one is required. `targetRacks` is only for NVL72 rack types (GB200/GB300), at 18 nodes per rack. — [Node Pools](https://docs.coreweave.com/products/cks/nodes/nodes-and-node-pools); [Create a Node Pool](https://docs.coreweave.com/products/cks/nodes/create)
  - `autoscaling` (bool), `minNodes` and `maxNodes`. CKS uses the upstream Kubernetes Cluster Autoscaler. Autoscaling is rejected for rack-based types, and the changelog marks it as preview. — [Autoscale Node Pools](https://docs.coreweave.com/products/cks/nodes/autoscaling); [NVL72 instances](https://docs.coreweave.com/platform/instances/nvl72)
  - `prefill` (`enabled`, `timeout`, `maxNodes`) requires `autoscaling: false` and `computeClass: default`, and is not supported on Spot. Its status is `status.prefillNodes`. — [Manage Node Pools](https://docs.coreweave.com/products/cks/nodes/manage); [Node Pool status](https://docs.coreweave.com/products/cks/nodes/nodepool-status)
  - `nodeLabels`, `nodeAnnotations` and `nodeTaints` are applied to every node in the pool. — [Node Pool reference](https://docs.coreweave.com/products/cks/reference/node-pool)
  - `lifecycle.scaleDownStrategy` is `IdleOnly` (default; removes only idle nodes) or `PreferIdle` (idle first, then active). — [Node Pool reference](https://docs.coreweave.com/products/cks/reference/node-pool)
  - `lifecycle.disableUnhealthyNodeEviction` (bool) appears only in a sample `kubectl describe` output in the how-to, not in the main reference. — [Create a Node Pool](https://docs.coreweave.com/products/cks/nodes/create)
  - `nodeConfigurationUpdateStrategy.type` is `Manual`, `OnSpecUpdate` (default) or `Always`. With `Manual`, staged updates wait in `pendingNodeConfigurations` and are applied with cwic. — [Node Pool reference](https://docs.coreweave.com/products/cks/reference/node-pool)
- **Scaling.** Scale with `kubectl scale nodepool <name> --replicas N` (a scale subresource) or by editing `targetNodes`. Check progress with `kubectl get nodepool`, where the `CURRENT` column should reach `TARGET`. — [Manage Node Pools](https://docs.coreweave.com/products/cks/nodes/manage)
- **Quota.** Quota is checked every time `targetNodes` is set. If the target exceeds quota, the pool fills only to the allowed amount; for example, a target of 15 against a quota of 10 yields 10 nodes. — [Manage Node Pools](https://docs.coreweave.com/products/cks/nodes/manage)
- **Status fields** — [Node Pool status](https://docs.coreweave.com/products/cks/nodes/nodepool-status); [Node configuration release note, 2026-02-04](https://docs.coreweave.com/release-notes/nodeprofile-ux-updates):
  - `status.currentNodes`: nodes booted, registered with the pool and in the cluster.
  - `status.prefillNodes`.
  - `status.nodeProfile`: the active node configuration (ncore image version, GPU driver version, Kubernetes version).
  - `pendingNodeConfiguration(s)`: staged configurations, with `createdAt` and `nodeProfile`.
- **Current conditions** (after the 2025-06-30 transition) — [Node Pool status](https://docs.coreweave.com/products/cks/nodes/nodepool-status); [Node Pool condition transition, 2025-06-30](https://docs.coreweave.com/release-notes/node-pool-conditions):
  - `Validated`: `Valid` / `Invalid` / `InternalError`. `Invalid` blocks delivery, for example for an unsupported instance type.
  - `Capacity`: `Sufficient` / `Partial` / `NoneAvailable` / `QueuedAwaitingCapacity`.
  - `Quota`: e.g. `Under`.
  - `AtTarget`: e.g. `TargetMet`.
  - **Deprecated / no longer maintained:** `Accepted`, `Allocated`, `SufficientCapacity`.
  - A healthy pool shows `Validated: Valid`, `Capacity: Sufficient`, `Quota: Under` and `AtTarget: TargetMet`.
- **Node configuration management through cwic** — [cwic README](https://github.com/coreweave/cwic/blob/main/README.md):
  - `cwic nodepool upgrade <np>` applies a pending node profile.
  - `cwic nodepool rollback <np> [<nodeprofile>]`.
  - `cwic nodepool rollout start|stop <np>` serves a "RolloutOnCommand Reconfiguration Strategy".
  - `cwic nodepool node get <np> --requiring-reconfiguration`.
  
  These imply a companion node-profile object, named "nodeprofile" in the CLI, whose kind and group I did not find.
- **Node lifecycle labels** — [Node state transitions](https://docs.coreweave.com/platform/fleet-management/node-lifecycle/state-transitions); [Node cordoning](https://docs.coreweave.com/products/cks/nodes/cordon):
  - Every node carries `node.coreweave.cloud/state`, with values such as `production`, `zap` and `triage`.
  - During a pending transition it also carries `node.coreweave.cloud/pending-state`.
  - `node.coreweave.cloud/reserved` equals the customer Org ID when `state=production`; otherwise it equals the state.
  - On a pending transition CKS cordons the node and records the reason in the annotation `node.coreweave.cloud/cordonReason`, e.g. `NLCCPendingExitProduction`.
  - `CWNCPhasePendingTenantWorkloadRunning` is reported while tenant workloads block a pending transition.
- **Other node labels** seen in CoreWeave's own code: `node.coreweave.cloud/type` (GPU/CPU type), `node.coreweave.cloud/class`, `node.coreweave.cloud/cluster` and `cks.coreweave.com/org-id`. Benchmark manifests use node affinity on `node.coreweave.cloud/state` and `node.coreweave.cloud/reserved`. — [reference-architecture arena k8s.py](https://github.com/coreweave/reference-architecture/blob/main/arena/notebooks/lib/k8s.py); [reference-architecture warp-gb200-config.yaml](https://github.com/coreweave/reference-architecture/blob/main/storage/warp-benchmark/warp-gb200-config.yaml)
- **cwic node operations.** cwic says "CKS stores a lot of important information as metadata on kubernetes node objects including hardware information… lifecycle actions, pending action, health check success". It exposes `cwic node cordon|uncordon|drain|undrain|reboot [--safe|--force]|verify|shell|view`. — [cwic README](https://github.com/coreweave/cwic/blob/main/README.md)
- **Control-plane node pool (historical).** Clusters created before 2025-07-07 still have a `cpu-control-plane` node pool; CoreWeave support migrates them per customer. — [Control Plane Node Pools release note, 2025-07-07](https://docs.coreweave.com/release-notes/control-plane-node-pools)

### Inferences
- **Two planes, two credentials.** A programmatic client needs two:
  1. The cloud API (Connect, `api.coreweave.com`, CoreWeave token) for VPC and cluster.
  2. The cluster's Kubernetes API (kubeconfig) for `NodePool`.
  
  This means node pools cannot be created until the cluster is `RUNNING`, a kubeconfig is obtained and, for private clusters, the endpoint is reachable (for example via Tailscale or from inside the VPC).
- **Async model.** Node-pool sizing is level-triggered, Kubernetes-style: a desired `targetNodes` plus conditions. There is no operation object or provisioning RPC.

### Gaps
- I could not retrieve the full OpenAPI v3 schema of the `NodePool` CRD. Still unconfirmed:
  - semantics of `nodeConfigurationUpdateStrategy: Always`
  - full `AtTarget` and `Quota` reason sets
  - printer columns beyond CURRENT/TARGET
  - whether fields such as image or driver pinning exist in spec
- I found no evidence of a reservation or capacity-reservation **field** on NodePool. Reservation appears to be a billing and contract attribute: "billing for default Node Pools depends on Reservation and utilization". I also found no node-level "reservation" CRD.
- I could not identify the API group and kind of the "nodeprofile" object used by `cwic nodepool rollback`.

## 4. CRUD operations and semantics: async provisioning, polling, update semantics, error model

### Takeaway
All CKS v1beta1 mutations return the `Cluster` **immediately**, and provisioning is asynchronous. Clients poll `GetCluster` until `status` reaches a terminal value:
- create: `CREATING` → `RUNNING`
- update: `UPDATING` / `UPGRADING` → `RUNNING`
- delete: `DELETING` → `DELETED` or NotFound

There are no operation objects. Updates use PATCH with a `google.protobuf.FieldMask`. The API has known quirks around field presence and append-only or immutable fields. Errors are standard Connect/gRPC codes with `google.rpc.errdetails` payloads.

### Cited Findings
- **Responses.** `CreateClusterResponse`, `UpdateClusterResponse` and `DeleteClusterResponse` each wrap a `Cluster`. `ListClustersResponse` has `items[]` and `unreachable[]`. — [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
- **Polling in the official Terraform provider** — [provider resource_cluster.go](https://github.com/coreweave/terraform-provider-coreweave/blob/main/coreweave/cks/resource_cluster.go):
  - **Create:** pending states `STATUS_CREATING` and `STATUS_UNSPECIFIED`; target `STATUS_RUNNING`; timeout **45 minutes**, raised from a lower value in an earlier release. On `STATUS_FAILED` the provider saves state and reports "You must delete and recreate this cluster to retry".
  - **Update:** pending states `STATUS_UPDATING`, `STATUS_UPGRADING` and `STATUS_UNSPECIFIED`; target `RUNNING`; timeout 20 minutes.
  - **Delete:** pending `STATUS_DELETING`; target `STATUS_DELETED`, with `connect.CodeNotFound` treated as deleted; timeout 20 minutes.
  - A cluster in `STATUS_FAILED` forces replacement (`requireReplaceIfStatusFailed`).
- **VPC polling.** VPC create, update and delete poll to `VPC_STATUS_READY` or deletion with 20-minute timeouts. — [provider resource_vpc.go](https://github.com/coreweave/terraform-provider-coreweave/blob/main/coreweave/networking/resource_vpc.go)
- **Update mask.** `UpdateClusterRequest` contains:
  - `update_mask` (FieldMask, OPTIONAL): "If not set, all fields will be updated."
  - `id` (REQUIRED)
  - `public`, `version` (marked **REQUIRED** in the proto)
  - `oidc`, `authz_webhook`, `authn_webhook`, `audit_policy`
  - `network` (an `UpdateClusterRequest.Network` holding only `internal_lb_cidr_names` and `service_node_port_range`)
  - `disable_oidc_info_publication`, `kubelet`, `additional_server_sans`, `tailscale`
  
  Name, zone, VPC, and pod and service CIDRs cannot be updated. — [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
- **Field-presence quirk.** The provider comment reads: "the CKS API validates field presence on the raw request (e.g. rejecting non-nil network config during a version upgrade), ignoring UpdateMask. The mask is still set as a signal to the server for forward compatibility." The provider therefore sends only changed fields. This changed in v0.13.0, 2026-04-02. Mask paths it uses include:
  - `public`, `version`, `audit_policy`
  - `network.internal_lb_cidr_names`, `network.service_node_port_range.start|end`
  - `oidc.issuer_url` … `oidc.admin_group_binding`
  - `authn_webhook.server|ca`, `authz_webhook.server|ca`
  - `additional_server_sans`, `tailscale.client_id`, `kubelet`
  - Sources: [provider resource_cluster.go](https://github.com/coreweave/terraform-provider-coreweave/blob/main/coreweave/cks/resource_cluster.go); [CHANGELOG](https://github.com/coreweave/terraform-provider-coreweave/blob/main/CHANGELOG.md)
- **Mutability rules** enforced by the provider — [TF cks_cluster docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/resources/cks_cluster.md); [provider resource_cluster.go](https://github.com/coreweave/terraform-provider-coreweave/blob/main/coreweave/cks/resource_cluster.go):
  - `internal_lb_cidr_names` is append-only; removing an entry forces replacement.
  - `node_port_range` "can be expanded in existing clusters but not shrunk"; shrinking forces replacement. Update support was added in v0.9.0, 2026-01-13.
  - `name`, `zone`, `vpc_id`, `pod_cidr_name`, `service_cidr_name`, the v6 CIDR names and `shared_storage_cluster_id` all require replacement.
- **Kubernetes version upgrades** — [Upgrade Kubernetes](https://docs.coreweave.com/products/cks/clusters/upgrade-kubernetes); [CKS Kubernetes upgrade support, 2025-07-31](https://docs.coreweave.com/changelog/release-notes/cks-kubernetes-upgrade):
  - Upgrade by `PATCH` with the next minor `version`; the cluster's upgradeable flag must be true.
  - Upgrades are one minor step at a time, with no skipping and no downgrades.
  - They are blocked if deprecated APIs are in use, the cluster is unhealthy, or nodes are more than two versions behind.
  - Afterwards, nodes must be rebooted per node pool so kubelets match.
  - The docs call the flag `isUpgradable`; the proto JSON name is `isUpgradeable`.
- **Supported Kubernetes versions.** CKS supports the three most recent Kubernetes minors, with a new one about every 120 days. — [Cluster components](https://docs.coreweave.com/products/cks/reference/cluster-components)
  - That page lists v1.35–v1.37, with **v1.37 as the default** for new clusters and cgroup v2 required for v1.35+. — [Cluster components](https://docs.coreweave.com/products/cks/reference/cluster-components)
  - This conflicts with the changelog, which says v1.36 is the default. — [Changelog](https://docs.coreweave.com/changelog)
  - The Terraform example uses `v1.35`. — [TF cks_cluster docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/resources/cks_cluster.md)
- **Multi-zone listing.** `ListClustersRequest.return_partial_success` "will opt the request into returning the clusters in reachable zones and will populate unreachable zones in the `ListClustersResponse.unreachable` field. This can only be set to `true` if the request is made to the global endpoint." This field was added between the 2026-03-26 and 2026-06-30 module revisions. There is **no pagination** (no page token or page size) in v1beta1 List, and v1beta2 List adds only a CEL `filter`. — [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
- **Idempotency.** v1beta1 has no idempotency key. v1beta2 adds a UUID `request_id` on Create, Update and Delete ("This request is only idempotent if a `request_id` is provided"), plus `allow_missing` on Delete and a client-supplied UUID `cluster_id` on Create. — [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
- **Error model.** The provider maps Connect codes to messages and unpacks `google.rpc` error details — [provider client.go](https://github.com/coreweave/terraform-provider-coreweave/blob/main/coreweave/client.go):
  - `NotFound` and `AlreadyExists` → `errdetails.ResourceInfo`
  - `FailedPrecondition` → `PreconditionFailure`
  - `InvalidArgument` → `BadRequest.FieldViolations`
  - `ResourceExhausted` → `QuotaFailure`
  - `ErrorInfo.Reason`, plus `metadata["current_etag"]`
  - also handled: `Unauthenticated`, `PermissionDenied`, `Unavailable`, `DeadlineExceeded`, `Canceled`
- **Retry policy.** HTTP 429 and 5xx (except 501) are retried with jittered exponential back-off: up to 10 retries, 200 ms – 5 s waits, and a 10 s default per-attempt timeout. — [provider retry.go](https://github.com/coreweave/terraform-provider-coreweave/blob/main/coreweave/retry.go); [provider index docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/index.md)

### Inferences
- **No conflict control in v1beta1.** There are no etags or resource versions on CKS v1beta1 `Cluster`, so concurrent updates are last-writer-wins. The `current_etag` error metadata the provider handles appears to belong to other CoreWeave services, such as object storage or registry.
- **Shallow FieldMask.** Because FieldMask is honoured inconsistently, safe clients should send only the fields being changed and populate the mask, as Terraform does.

### Gaps
- I found no documented rate limits, request quotas or HTTP status mapping beyond the Connect codes.
- I did not find the "global endpoint" vs zonal endpoint hostnames in any accessible source.

## 5. Authentication and authorization

### Takeaway
- **Cloud API.** Access uses a **bearer CoreWeave API access token** of the form `CW-SECRET-<secret>`. Tokens are created in the Cloud Console, are **user-scoped**, and inherit the owner's IAM roles (for example **CKS Admin** or **CKS Viewer**), which are granted by IAM Access Policies.
- **Kubernetes access.** Either "Managed Auth" (the same CoreWeave token embedded in a Console-downloaded kubeconfig, or used via the cwic exec plugin) or "Unmanaged Auth" (cluster `oidc` or webhook configuration, with `admin_group_binding` for cluster-admin bootstrap).
- **Tailscale.** Tailscale-based access is a third option.
- **Workload identity.** Each cluster's service-account issuer can be published as a public OIDC provider at `oidc.cks.coreweave.com/id/<cluster-id>`.

### Cited Findings
- **Token format and provider config.** The provider token is "CoreWeave API Token in the form `CW-SECRET-<secret>`". It can also come from `COREWEAVE_API_TOKEN`, which takes precedence. The provider sends `Authorization: Bearer <token>`, and v0.23.0 (2026-09-03) added "refreshable access token source support". — [provider index docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/index.md); [provider internal/auth](https://github.com/coreweave/terraform-provider-coreweave/tree/main/internal/auth); [CHANGELOG](https://github.com/coreweave/terraform-provider-coreweave/blob/main/CHANGELOG.md)
- **Token scope and Console management.** Tokens are generated in the Cloud Console (`console.coreweave.com/tokens`). They are user-scoped and inherit the owner's IAM roles. The Access Tokens dashboard only lists and deletes tokens and does not show permissions. A 403 on every request means the token is missing, malformed, expired or revoked; a 403 on specific actions means the role is missing. — [API access tokens](https://docs.coreweave.com/products/cks/auth-access/managed-auth/api-access); [cwic README](https://github.com/coreweave/cwic/blob/main/README.md)
- **Roles.** **CKS Admin** can create, update and delete clusters and VPC resources and is required to change cluster config through the API. **CKS Viewer** is read-only. Both are assigned via IAM Access Policies. — [CKS auth introduction](https://docs.coreweave.com/products/cks/auth-access/introduction); [API access tokens](https://docs.coreweave.com/products/cks/auth-access/managed-auth/api-access)
- **Legacy groups.** The legacy permission groups (`admin`, `write`, `read`) map to IAM roles such as CKS Admin and CKS Viewer, and to Kubernetes `cluster-admin`, `edit` and `view`. — [Legacy user permissions](https://docs.coreweave.com/security/iam/access-policies/legacy-permissions)
- **Caller identity.** Provider v0.22.0 (2026-08-26) added data source `coreweave_caller_identity`, which returns `organization_id` and `principal_id` for the token. cwic's `auth whoami` queries a "WhoAmI service" and maps permissions to access-policy role names. — [TF caller_identity docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/data-sources/caller_identity.md); [cwic README](https://github.com/coreweave/cwic/blob/main/README.md)
- **Managed Auth (recommended).** Console-generated tokens are embedded in kubeconfigs scoped to a designated CKS cluster, and one token can grant access to multiple clusters. Kubernetes RBAC then governs in-cluster permissions. — [Managed Auth / kubeconfig](https://docs.coreweave.com/products/cks/auth-access/managed-auth/kubeconfig)
- **Kubeconfig generation with cwic** — [cwic README](https://github.com/coreweave/cwic/blob/main/README.md):
  - `cwic cluster auth <name>|all` generates kubeconfigs. "Only clusters with a Public endpoint are supported".
  - By default the kubeconfig embeds a static token.
  - With `--cwic-auth`, the kubeconfig uses an exec block (`cwic auth k8s api-token --org-id cwXXXX`, which emits `client.authentication.k8s.io/v1 ExecCredential`).
  - With `--oidc-auth -- <cmd>`, it uses a custom OIDC exec plugin. OIDC kubeconfigs target the "unmanaged endpoint, `https://api.<apiServerEndpoint>`".
- **Unmanaged Auth (OIDC).** `cwic cluster oidc config get|update` reads and patches `oidc.*` through the CKS API with an explicit field mask and works for private clusters. `--admin-group-binding` sets `oidc.adminGroupBinding`; "CKS reconciles the `cluster-admin` binding". "CKS applies updates asynchronously; wait for the cluster to return to RUNNING". — [cwic README](https://github.com/coreweave/cwic/blob/main/README.md)
- **Deprecated auth endpoint.** An older unmanaged-authentication endpoint is deprecated. — [Changelog](https://docs.coreweave.com/changelog)
- **Tailscale.** There are three access modes: Tailscale Managed Auth, CoreWeave Managed Auth and Direct Auth. The cluster API field is `tailscale.client_id`, described as "Tailscale Client ID for the federated identity", with output `tailnet_domain`. It was added to the provider in v0.16.0, 2026-06-30. — [Tailscale VPN access](https://docs.coreweave.com/products/cks/auth-access/tailscale-vpn); [buf.build/coreweave/cks](https://buf.build/coreweave/cks); [CHANGELOG](https://github.com/coreweave/terraform-provider-coreweave/blob/main/CHANGELOG.md)
- **Workload identity (pod → cloud).**
  - Clusters publish their SA issuer at `oidc.cks.coreweave.com/id/<cluster-id>` unless `disable_oidc_info_publication` is set. — [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
  - CoreWeave's examples use the projected token `cks.coreweave.com/serviceaccount/cks-pod-identity-token` and the `api.coreweave.com/v1/cwobject/temporary-credentials/oidc/` exchange. — [reference-architecture repo](https://github.com/coreweave/reference-architecture)
  - Org-level **Workload Federation** OIDC configs (`coreweave.workload_federation.control_plane.v1beta1`, Terraform `coreweave_workload_federation_oidc_config`, added in v0.23.0, 2026-09-03; cwic `iam workload-federation oidc create --name --issuer-url --audience`) let external IdPs such as GitHub Actions authenticate to CoreWeave. — [cwic README](https://github.com/coreweave/cwic/blob/main/README.md); [CHANGELOG](https://github.com/coreweave/terraform-provider-coreweave/blob/main/CHANGELOG.md)
- **Organization identifiers.** IDs appear as `cwXXXX`, e.g. node label `cks.coreweave.com/org-id=cw623e`. — [reference-architecture arena k8s.py](https://github.com/coreweave/reference-architecture/blob/main/arena/notebooks/lib/k8s.py)

### Inferences
- **No resource hierarchy.** The IAM model is org → principals (users, plus federated workloads) → Access Policies binding roles. There are no projects or folders, so a token is implicitly scoped to one org, and no API request carries an org or project ID.
- **Short-lived credentials.** Machine-to-machine access with short-lived tokens is emerging: workload federation and refreshable token sources both arrived around 2026-08/09. Previously only static user tokens were available.

### Gaps
- I found no evidence of service accounts or non-user API tokens other than workload federation. I also could not verify token expiry and TTL options or fine-grained, resource-scoped permissions for a token.
- I found no direct "GetKubeconfig" RPC in v1beta1. Kubeconfig comes from the Console or cwic, apparently assembled client-side from `api_server_endpoint` plus the token.
- I could not confirm the details of SAML/SSO console login beyond the IAM pages.

## 6. Tooling: Terraform, CLI, SDKs, Console

### Takeaway
- **Terraform.** The official Terraform provider `coreweave/coreweave` (v0.24.0, 2026-09-11) covers `coreweave_cks_cluster` and `coreweave_networking_vpc`, plus registry, object storage, inference, sandbox, workload federation and caller identity. It has **no node pool resource**, so you use the `kubernetes` provider with the NodePool CRD.
- **CLI.** The CLI is **cwic** (CoreWeave Intelligent CLI, open source on GitHub, marked "in-development … as-is").
- **SDKs.** SDKs are the Buf-generated clients, e.g. `buf.build/gen/go/coreweave/cks/...`. I found no hand-written Go or Python SDK.

### Cited Findings
- **Provider configuration.** Settings are `token`, `endpoint` (default `https://api.coreweave.com/`), `s3_endpoint` (default `https://cwobject.com`) and `http_timeout`. — [provider index docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/index.md)
- **`coreweave_cks_cluster` schema** — [TF cks_cluster docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/resources/cks_cluster.md):
  - Required: `name`, `zone`, `vpc_id`, `version`, `pod_cidr_name`, `service_cidr_name`, `internal_lb_cidr_names`.
  - Optional:
    - `public`, `audit_policy`, `oidc{...}`, `authn_webhook{server,ca}`, `authz_webhook{server,ca}`
    - `node_port_range{start,end}`
    - `pod_cidr_name_v6`, `service_cidr_name_v6`, `internal_lb_cidr_names_v6`
    - `additional_server_sans`, `kubelet` (JSON), `shared_storage_cluster_id`, `tailscale{client_id}`
  - Read-only: `id`, `api_server_endpoint`, `service_account_oidc_issuer_url`, `status`.
  - Import is by `id`.
  - The Terraform schema flattens `network.*` and renames `signing_algorithms` to `signing_algs`.
- **`coreweave_networking_vpc` schema** — [TF networking_vpc docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/resources/networking_vpc.md):
  - Required: `name`, `zone`.
  - Optional:
    - `host_prefixes` (set of `{name,type,prefixes,ipam{prefix_length,gateway_address_policy}}`)
    - `host_prefix` (**deprecated** since v0.10.0, 2026-01-29)
    - `vpc_prefixes` (set of `{name,value}`)
    - `egress{disable_public_access}`, `ingress{disable_public_services}`, `dhcp{dns{servers}}`
  - Read-only: `id`.
- **Data sources.** The provider has data sources `coreweave_cks_cluster` (by `id`), `coreweave_networking_vpc`, `coreweave_caller_identity` and `coreweave_workload_federation_oidc_config`, plus registry and inference parameters. — [provider docs/data-sources](https://github.com/coreweave/terraform-provider-coreweave/tree/main/docs/data-sources)
- **Release timeline (CKS/VPC-relevant)** — [CHANGELOG](https://github.com/coreweave/terraform-provider-coreweave/blob/main/CHANGELOG.md):

  | Version | Date | Change |
  |---|---|---|
  | v0.1.x | 2025-01-31 | Early releases |
  | v0.4.0 | 2025-09-23 | `service_account_oidc_issuer_url` |
  | v0.5.0 | 2025-10-09 | `admin_group_binding` |
  | v0.6.0 | 2025-10-24 | `shared_storage_cluster_id` |
  | v0.9.0 | 2026-01-13 | `node_port_range` updates |
  | v0.10.0 | 2026-01-29 | `host_prefixes`; `host_prefix` deprecated |
  | v0.11.0 | 2026-02-12 | IPv6 dual-stack fields |
  | v0.12.0 | 2026-03-12 | `additional_server_sans` |
  | v0.13.0 | 2026-04-02 | Changed-fields-only updates |
  | v0.16.0 | 2026-06-30 | Tailscale |
  | v0.19.0 | 2026-07-23 | Kubelet overrides |
  | v0.22.0 | 2026-08-26 | Caller identity |
  | v0.23.0 | 2026-09-03 | Refreshable tokens; workload federation |
  | v0.24.0 | 2026-09-11 | Latest release |
- **cwic capabilities** — [cwic README](https://github.com/coreweave/cwic/blob/main/README.md):
  - `auth login|whoami|switch|logout|token|k8s api-token|accesskey`
  - `cluster get|auth|oidc config`
  - `node …`, `nodepool …`, `sunk …`, `cwobject …`, container registry, `iam workload-federation oidc …`, and DFS verification
  - Install via GitHub releases, or build from source (Go ≥1.24.1).
- **Pulumi.** A Pulumi registry package `coreweave` with a `CksCluster` resource exists. It appears to be bridged from the Terraform provider, but I did not verify this. — [Pulumi registry](https://www.pulumi.com/registry/packages/coreweave/api-docs/ckscluster/)
- **Generated SDKs.** The Go SDKs are `buf.build/gen/go/coreweave/cks/{protocolbuffers,connectrpc}/go` and `buf.build/gen/go/coreweave/networking/...`, downloadable through Buf's Go module proxy. I verified this by downloading `@latest`. — [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
- **Other clients.** cwic's README mentions an MCP endpoint `mc.coreweave.com/mcp` and docs at `docs.coreweave.com/mcp`. These are agent-tooling surfaces, and I did not investigate them. — [cwic / reference-architecture repo grep](https://github.com/coreweave/cwic/blob/main/README.md)

### Inferences
- **Other languages.** Buf's remote generation also serves other languages, for example Python via Buf's package index, so Python and TypeScript clients can be generated from the same modules. I did not confirm any CoreWeave-maintained Python SDK.
- **Two Terraform providers.** An IaC stack for a full cluster with node pools needs both the `coreweave` and `kubernetes` providers, plus a two-phase apply, because the kubeconfig only exists after the cluster does.

### Gaps
- I found no official Python SDK, and no `coreweave` CLI distinct from cwic.
- I could not access the Cloud Console UX details.

## 7. Limitations, quirks, recent changes (2025–2026) and the legacy CoreWeave Cloud → CKS transition

### Takeaway
The CKS API is still **beta** (`v1beta1`), with a not-yet-documented `v1beta2` redesign already in the Buf module. Notable quirks:
- partial FieldMask honouring
- append-only and immutable networking fields
- no pagination or idempotency in v1beta1
- name-based CIDR references
- node pools living only in-cluster as a `v1alpha1` CRD

The 2025–2026 changes add dual-stack, kubelet overrides, Tailscale, extra SANs, self-serve K8s upgrades, Spot pools, node-profile management and new NodePool conditions. I found no primary source describing the legacy namespace-based "CoreWeave Cloud" migration path.

### Cited Findings
- **Quirks:**
  - The UpdateMask is ignored for field-presence validation. — [provider resource_cluster.go](https://github.com/coreweave/terraform-provider-coreweave/blob/main/coreweave/cks/resource_cluster.go)
  - `UpdateClusterRequest.version` is marked REQUIRED even for non-version updates. — [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
  - `internal_lb_cidr_names` is append-only. — [TF cks_cluster docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/resources/cks_cluster.md)
  - The NodePort range can only grow. — [TF cks_cluster docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/resources/cks_cluster.md)
  - A failed cluster must be deleted and recreated. — [provider resource_cluster.go](https://github.com/coreweave/terraform-provider-coreweave/blob/main/coreweave/cks/resource_cluster.go)
  - The cluster name is limited to 30 characters. — [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
  - `kubelet` changes require a node reboot. — [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
  - cwic kubeconfig generation supports only public clusters. — [cwic README](https://github.com/coreweave/cwic/blob/main/README.md)
- **Docs/proto naming mismatch.** The docs call the upgrade flag `isUpgradable`; the proto JSON name is `isUpgradeable`. — [Upgrade Kubernetes](https://docs.coreweave.com/products/cks/clusters/upgrade-kubernetes); [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
- **Recent API changes.** Between the 2026-03 and 2026-06 module revisions, v1beta1 gained `STATUS_PREEMPTED` documentation and `ListClustersRequest.return_partial_success` / `ListClustersResponse.unreachable`. These point to a global endpoint fanning out to zonal backends. — [buf.build/coreweave/cks](https://buf.build/coreweave/cks)
- **2025 milestones:**
  - Self-serve Kubernetes upgrade support (2025-07-31). — [CKS Kubernetes upgrade support](https://docs.coreweave.com/changelog/release-notes/cks-kubernetes-upgrade)
  - Kubernetes v1.33 available (2025-08-26). — [CKS Kubernetes v1.33](https://docs.coreweave.com/release-notes/cks-kubernetes-v1-33)
  - Control-plane node pool change (2025-07-07). — [Control Plane Node Pools](https://docs.coreweave.com/release-notes/control-plane-node-pools)
  - Node Pool condition transition (2025-06-30). — [Node Pool condition transition](https://docs.coreweave.com/release-notes/node-pool-conditions)
- **2026 milestones:**
  - Node configuration (node profile) visibility and management (2026-02-04). — [nodeprofile release note](https://docs.coreweave.com/release-notes/nodeprofile-ux-updates)
  - Spot Node Pools and Capacity plans (2026-03-10). — [Spot release note](https://docs.coreweave.com/release-notes/spot-node-pools)
  - Capacity Finder for comparing Spot capacity across AZs (2026-05-22). — [Capacity Finder](https://docs.coreweave.com/release-notes/capacity-finder)
- **Deprecated items:**
  - VPC `host_prefix`, replaced by `host_prefixes`. — [TF networking_vpc docs](https://github.com/coreweave/terraform-provider-coreweave/blob/main/docs/resources/networking_vpc.md)
  - NodePool conditions `Accepted`, `Allocated` and `SufficientCapacity`. — [Node Pool condition transition](https://docs.coreweave.com/release-notes/node-pool-conditions)
  - The old unmanaged-auth endpoint. — [Changelog](https://docs.coreweave.com/changelog)
  - The `cpu-control-plane` node pool on pre-2025-07-07 clusters. — [Control Plane Node Pools](https://docs.coreweave.com/release-notes/control-plane-node-pools)
  - Kubernetes versions older than the supported three, which may keep running with limited support. — [Changelog](https://docs.coreweave.com/changelog)
- **Legacy CoreWeave Cloud.** The search found no current CoreWeave page describing the legacy namespace-based CoreWeave Cloud or an official migration guide to CKS. The closest references are "Legacy user permissions", which maps legacy `admin`/`write`/`read` groups to CKS roles, and per-customer support-assisted migrations. — [Legacy user permissions](https://docs.coreweave.com/security/iam/access-policies/legacy-permissions); [Control Plane Node Pools](https://docs.coreweave.com/release-notes/control-plane-node-pools)
  - The `coreweave/kubernetes-cloud` GitHub repo, which held examples for the legacy platform, still exists. I did not inspect its contents. — [github.com/coreweave/kubernetes-cloud](https://github.com/coreweave/kubernetes-cloud)

### Inferences
- **Legacy model removed.** The legacy shared, namespace-per-tenant model has effectively been removed from the documentation. Current docs, APIs and tooling assume dedicated, single-tenant CKS clusters on bare-metal nodes; cwic describes "fully isolated k8s clusters that run on bare-metal nodes".
- **Comparison caution.** When comparing with Nebius mk8s, treat CKS `v1beta1` as the stable surface, and treat `v1beta2` (with `request_id`, conditions, resource names and Metadata messages) as the likely future shape.

### Gaps
- I found no primary source with an end-of-life date for legacy CoreWeave Cloud, and no 2025–2026 migration announcement.
- I could not see the full changelog entries for 2026 H2. Specifically, I could not verify release notes for Kubernetes v1.36 and v1.37 availability or for the kubelet-override and Tailscale features; the dates above are Terraform provider release dates, not platform GA dates.
- `docs.coreweave.com` could not be fetched directly in this session. All docs-derived facts came via search extraction and should be spot-checked, especially the NodePool field list and the condition reasons.
