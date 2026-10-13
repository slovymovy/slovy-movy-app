# Nebius AI Cloud Managed Service for Kubernetes (mk8s): product features for AI/GPU workloads (as of Oct 2026)

> Source-access note: direct fetches of docs.nebius.com and nebius.com were blocked by this session's egress policy (HTTP 403). Facts from those domains come from search-engine extraction of the cited pages. GitHub pages (nebius/soperator, nebius-solutions-library, nebius-k8s-applications, cncf/k8s-conformance) were read directly. Spot-check exact numbers against the live docs before publishing. Dates are given where the source shows them.

## Control plane: Kubernetes versions & upgrades, HA/etcd, SLA, pricing, endpoints, audit logging

### Takeaway
The mk8s control plane is free. It is HA by default (3-node etcd), and its 99.9% SLA applies only to HA configurations. Supported versions are N-2 (1.33/1.34/1.35, with 1.35 the default), and 1.36 passed CNCF conformance in Sep 2026. Upgrades are manual: there is no automatic control-plane or node-group upgrade. The API server has a public endpoint (optional CIDR allowlist) and a private endpoint. Audit logs are opt-in. Every cluster has a public OIDC discovery endpoint for workload identity.

### Cited Findings
- **Supported versions:** 1.35, 1.34 and 1.33 are currently supported, and 1.35 is recommended for new clusters. Versions 1.30–1.32 are past end of life and kept only for older clusters — [Nebius docs: Kubernetes versions](https://docs.nebius.com/kubernetes/versions)
- The changelog says clusters and node groups now support 1.35, which is the default and recommended version — [Nebius changelog](https://docs.nebius.com/changelog). The Aether 3.6 release post (June 2026) said the platform's Kubernetes had been updated to 1.34 — [Nebius blog: Aether 3.6](https://nebius.com/blog/posts/ai-cloud-aether-3-6)
- **Kubernetes 1.36 conformance:** a CNCF conformance submission "Conformance results for v1.36/nebius" was opened Sep 7, 2026 and merged Sep 11, 2026. The bot reported "All requirements (15) have passed", and the reviewer wrote "You are now Certified Kubernetes". The PR page does not name the product — [cncf/k8s-conformance PR #4392](https://github.com/cncf/k8s-conformance/pull/4392)
- The Q3 2026 release notes (dated Oct 1, 2026) state "Nebius Managed Kubernetes achieved CNCF Kubernetes conformance certification" — [Nebius release notes Q3 2026](https://nebius.com/release-notes/posts/q3-2026); [Nebius blog: Q3 2026 updates](https://nebius.com/blog/posts/compute-cloud-platform-q3-2026)
- **Upgrade policy:**
  - Upgrades are manual, and automatic upgrade is not supported. The control plane is upgraded first, then node groups.
  - Node groups are not upgraded automatically together with the cluster, even when they were created with the same version.
  - A node group can't run a higher version than its control plane.
  - A non-HA control plane (one etcd store) makes the cluster unavailable during the control-plane upgrade.
  - [Nebius docs: Setting and upgrading versions](https://docs.nebius.com/kubernetes/manage-versions)
- The CLI accepts only MAJOR.MINOR versions (e.g., "1.31"). A patch-version update option is "to be added later" — [Nebius CLI ref: mk8s cluster update](https://docs.nebius.com/cli/reference/mk8s/v1alpha1/cluster/update)
- **HA / etcd:** `etcd_cluster_size` defaults to 3. A control plane with size 3 is called "Highly Available" because the API stays available if one control-plane instance fails — [Terraform: nebius_mk8s_v1_cluster](https://docs.nebius.com/terraform-provider/reference/resources/mk8s_v1_cluster)
- Clusters are created with three etcd stores by default, and HA "does not affect the cost of the cluster" — [Nebius docs: Components](https://docs.nebius.com/kubernetes/components); [Nebius docs: Create/modify clusters](https://docs.nebius.com/kubernetes/clusters/manage)
- **SLA:** control-plane uptime target is 99.90%.
  - Credits are 10% of the service fee below 99.9% and 25% below 95%.
  - Downtime counts only when there is no customer access to the control plane for at least 5 minutes.
  - The SLA applies only to the HA configuration, defined as etcd cluster size 3–5.
  - Outages caused by the customer's firewall or routing are excluded.
  - The SLA document was published Sep 25, 2024 according to search metadata.
  - [Nebius SLA: Managed Kubernetes](https://docs.nebius.com/legal/sla-levels/managed-kubernetes)
- **Control-plane pricing:** Nebius's pricing page lists "Managed Service for Kubernetes: Free" — [Nebius prices](https://nebius.com/prices). A third-party review says "Managed Kubernetes, Slurm clusters and outbound traffic from VMs are free" — [getdeploying: Nebius](https://getdeploying.com/nebius). Nebius's terms say cost depends on cluster parameters and composition and list backups and outgoing traffic as separate charges — [Nebius terms: Managed Kubernetes](https://docs.nebius.com/legal/specific-terms/managed-kubernetes)
- **Public endpoint:**
  - A public endpoint is allocated by default.
  - If the allowed-CIDR list is unset or empty, access is not restricted.
  - With the public endpoint disabled, you can connect only from a VM in the same subnet as the cluster.
  - The private endpoint is a DNS name or IP reachable from the user VPC.
  - [Nebius docs: Create/modify clusters](https://docs.nebius.com/kubernetes/clusters/manage); [CLI: mk8s cluster create](https://docs.nebius.com/cli/reference/mk8s/cluster/create); [Terraform: mk8s_v1_cluster](https://docs.nebius.com/terraform-provider/reference/resources/mk8s_v1_cluster)
- **API-server IP allowlist:**
  - Entries must be IPv4 CIDRs with host bits zeroed.
  - The allowlist is enforced at the instance network-interface level through security groups.
  - It does not affect the private endpoint or internal traffic.
  - An update overwrites the existing list.
  - [Nebius docs: Access restriction for public endpoint](https://docs.nebius.com/kubernetes/networking/limit-access-to-public-endpoint)
  - Aether 3.6 (June 24, 2026) shipped the feature as "Managed Kubernetes API access control", with policy updates that need no downtime or cluster recreation — [Nebius blog: Aether 3.6](https://nebius.com/blog/posts/ai-cloud-aether-3-6); [Nebius newsroom: AI Cloud 3.6](https://nebius.com/newsroom/nebius-ai-cloud-3-6-strengthens-developer-experience-and-governance-for-production-operations)
- **Audit logs:**
  - Audit logs are disabled by default. Enable them with `nebius mk8s cluster update --id <id> --control-plane-audit-logs`.
  - They record metadata about operations that modify a cluster, e.g., who created or deleted a Pod and when.
  - Control-plane logs are enabled by default and cannot be disabled.
  - Audit logs can be queried with LogCLI under the label `sp_mk8s_audit_logs`.
  - [Nebius docs: Logs in Managed Kubernetes](https://docs.nebius.com/kubernetes/logs); [Nebius docs: LogCLI](https://docs.nebius.com/observability/logs/logcli)
- **Workload identity:** every mk8s cluster exposes a public OIDC discovery endpoint and a JWKS endpoint, so external systems can verify cluster-issued JWTs. These are used to register an OIDC IdP or to configure workload identity federation — [Nebius changelog](https://docs.nebius.com/changelog); [Nebius docs: OIDC public endpoint](https://docs.nebius.com/kubernetes/networking/oidc-public-endpoint)
  - Aether 3.6 added Workload Identity Federation (short-lived federated identities for workloads in external clouds) — [Nebius blog: Aether 3.6](https://nebius.com/blog/posts/ai-cloud-aether-3-6)
- **Access / kubeconfig:** `nebius mk8s cluster get-credentials` writes a kubeconfig, with `--external` for the public endpoint and `--internal` for the private one. A global `--impersonate-service-account-id` option runs commands under a service account — [CLI: get-credentials](https://docs.nebius.com/cli/reference/mk8s/cluster/get-credentials); [Nebius docs: Connect with kubectl](https://docs.nebius.com/kubernetes/connect)

### Inferences
- The control plane is free and HA by default. Nebius also says HA does not change cost, so HA plus SLA coverage is effectively a no-cost default. The SLA is void only if the user picks a 1-node etcd.
- The lack of auto-upgrade is a notable operational difference from hyperscalers and from providers with managed upgrade windows. The customer owns the timing of both control-plane and node-group upgrades.
- The versions page lists 1.33–1.35, but 1.36 passed conformance on Sep 11, 2026. 1.36 is likely newly available or rolling out as of Oct 2026. I did not confirm that from the versions page.

### Gaps
- Exact end-of-support dates per minor version, and whether any forced upgrade happens at EOL, were not retrieved. The versions page content was only partially visible through search.
- Whether the 99.9% SLA has been revised since Sep 2024 was not confirmed.
- How Nebius IAM roles map to Kubernetes RBAC (e.g., whether a `k8s.admin`-type role exists) was not found. The solution library says Terraform-managed RBAC is "disabled by default" and requires an approved access model — [k8s-training README](https://github.com/nebius/nebius-solutions-library/blob/main/k8s-training/README.md).
- No first-party statement was found on control-plane maintenance windows or notice periods.

## Compute: GPU/CPU platforms for node groups, preemptible/spot, reservations/capacity blocks, bare metal vs VM

### Takeaway
Node groups are Nebius Compute VMs; I found no bare-metal node option. Each node group is pinned to one platform+preset, which can't be changed later.
- **GPU platforms:**
  - H100 SXM and H200 SXM.
  - L40S PCIe.
  - RTX PRO 6000.
  - B200 SXM and B300 SXM (HGX, 8 GPUs per node).
  - GB300 NVL72, as `gpu-gb300` 4-GPU nodes grouped per rack with NVLink instance groups.
  - GB200 NVL72, announced GA in Europe in June 2025. No mk8s preset was found for it.
- **Capacity options:** preemptible node groups (dynamic spot pricing from Oct 2026) and capacity-block-group reservations with an optional PAYG fallback.

### Cited Findings
- **Platform table:**
  - eu-north1 (Finland): H200 NVLink (`gpu-h200-sxm`), H100 NVLink (`gpu-h100-sxm`), two L40S PCIe variants (Intel Ice Lake and AMD EPYC Genoa), plus CPU-only platforms.
  - `gpu-h200-sxm` is in eu-north1, eu-north2, eu-west1 and us-central1. `gpu-h100-sxm` is only in eu-north1.
  - us-central1 (Kansas City): B200 NVLink (`gpu-b200-sxm`), H200, RTX PRO 6000 (`gpu-rtx6000`), and CPU-only AMD EPYC Genoa.
  - [Nebius docs: VM & GPU types](https://docs.nebius.com/compute/virtual-machines/types); [Nebius docs: Regions](https://docs.nebius.com/overview/regions)
- B300 NVLink (`gpu-b300-sxm`) is available in uk-south1, eu-west2 and us-north1 — [Nebius docs: Regions](https://docs.nebius.com/overview/regions). The NCCL guide lists the B300 preset `8gpu-192vcpu-2768gb` in uk-south1, eu-west2 and us-north1 — [Nebius docs: NCCL tests](https://docs.nebius.com/kubernetes/gpu/nccl-test)
- **Presets:**
  - B200 (`gpu-b200-sxm`): `8gpu-160vcpu-1792gb`, in us-central1 — [Nebius docs: InfiniBand GPU clusters](https://docs.nebius.com/kubernetes/gpu/clusters)
  - H200: `8gpu-128vcpu-1600gb` — [Saturn Cloud install guide](https://saturncloud.io/docs/enterprise/nebius/). This is also the default GPU preset in Nebius's k8s-training solution, where "Only nodes with 8 GPU can be added to gpu cluster with infiniband connection" — [nebius-solutions-library k8s-training README](https://github.com/nebius/nebius-solutions-library/blob/main/k8s-training/README.md)
- **GB300 NVL72 on mk8s (Nebius solution library):**
  - A production rack is 18 `gpu-gb300` nodes on preset `4gpu-112vcpu-800gb`, i.e., 72 GPUs per rack.
  - Each rack gets one mk8s node group and one NVLink instance group.
  - Multi-rack deployments share one XDR InfiniBand fabric, which is required for 2+ racks.
  - GB300 needs Terraform provider ≥0.5.232 and a zero-surge rollout (`max_surge = 0`).
  - The solution rejects autoscaling, preemptible or public GPU nodes, MIG and custom drivers for GB300.
  - [k8s-training README](https://github.com/nebius/nebius-solutions-library/blob/main/k8s-training/README.md)
- The CLI added a `--template-nvlink-nvl-instance-group-id` flag to `mk8s node-group create` (entry dated 2026-06-25) — [Nebius CLI release notes](https://docs.nebius.com/cli/release-notes). The Terraform provider added a `template.nvlink` attribute to `nebius_mk8s_v1_node_group` — [Terraform provider release notes](https://docs.nebius.com/terraform-provider/reference/release-notes)
- **AI Cloud 3.1:** GB300 NVL72 and HGX B300 systems are "already being used by customers". Nebius calls itself the first provider globally to run production GB300 NVL72 on 800 Gbps Quantum-X800 InfiniBand. The exact date wasn't captured; likely late 2025 — [Nebius newsroom: AI Cloud 3.1](https://nebius.com/newsroom/nebius-ai-cloud-3-1-delivers-next-generation-nvidia-blackwell-ultra-compute-with-transparent-capacity-management-for-ai-at-scale); [Nebius blog: first live GB300 NVL72 in Europe](https://nebius.com/blog/posts/first-live-nvidia-gb300-nvl72-deployment-in-europe)
- **GB200 NVL72:** Nebius announced "the first general availability of NVIDIA GB200 Grace Blackwell Superchip capacity for customers in Europe" on Jun 11, 2025 — [Nebius newsroom: Blackwell GA in Europe](https://nebius.com/newsroom/nebius-delivers-first-nvidia-blackwell-general-availability-in-europe-brings-nvidia-ai-enterprise-to-nebius-ai-cloud). Self-service Blackwell — [Nebius blog: self-service Blackwell](https://nebius.com/blog/posts/introducing-self-service-nvidia-blackwell-gpus)
- **Node group immutability:** you cannot change the VM platform, preset or GPU cluster of an existing node group; create a new node group instead — [Nebius docs: Create/modify node groups](https://docs.nebius.com/kubernetes/node-groups/manage)
- Max 100 nodes per node group — [Nebius docs: Create/modify node groups](https://docs.nebius.com/kubernetes/node-groups/manage); [Nebius docs: Autoscaling](https://docs.nebius.com/kubernetes/node-groups/autoscaling)
- The default node-group update strategy replaces nodes one at a time (`max_unavailable=1, max_surge=0`). Node groups can use VPC security groups, and local SSDs are available on supported node groups — [Nebius changelog](https://docs.nebius.com/changelog)
- **B300 local NVMe:** mk8s combines the local disks into a volume for kubelet ephemeral storage, and the former `/scratch` mount was removed — [k8s-training README](https://github.com/nebius/nebius-solutions-library/blob/main/k8s-training/README.md)
- **Preemptible node groups** are supported for interruptible workloads — [Nebius docs: Create/modify node groups](https://docs.nebius.com/kubernetes/node-groups/manage)
- **Spot pricing (from Oct 2026):** preemptible GPU prices are dynamic by GPU type and region. They can change every 15 minutes and are capped at one cent below on-demand. "From" rates are $0.79/GPU-h for H100/H200/RTX PRO 6000 and $0.99 for B200/B300 — [btw.media](https://btw.media/en/nebius-gpu-spot-price-cost-cap-scheduling-rule)
  - Nebius's own pricing doc lists fixed preemptible rates of $0.95 for RTX PRO 6000 and $0.65 for L40S — [Nebius docs: Compute pricing](https://docs.nebius.com/compute/resources/pricing). This conflicts with the $0.79 figure, probably because of the transition timing.
  - Older third-party fixed preemptible rates (H200 $2.45, B200 $3.95, B300 $4.30) predate the switch and are stale — [getdeploying: Nebius](https://getdeploying.com/nebius)
- **Reservations:**
  - Reservations use capacity block groups, which a Nebius manager creates along with a commitment-discount addendum. GPUs in these groups don't count against GPU quotas — [Nebius docs: Capacity block groups](https://docs.nebius.com/overview/limits/capacity-block-groups); [Nebius docs: Reservations for node groups](https://docs.nebius.com/kubernetes/node-groups/reservations)
  - Only regular (non-preemptible) GPU node groups support reservations; preemptible and CPU-only groups don't — [Nebius docs: Reservations for node groups](https://docs.nebius.com/kubernetes/node-groups/reservations)
  - Usage modes: "with reservations" or "without" (common pool). Selection: "Any (existing and future)" (the default) or specific groups. "Switch to PAYG" either lets nodes start pay-as-you-go when reserved capacity is exhausted or ("Never") blocks the start.
  - If a reservation interval expires while a VM runs, the VM continues as PAYG.
  - [Nebius docs: Create/modify clusters](https://docs.nebius.com/kubernetes/clusters/manage); [Nebius docs: Create/modify node groups](https://docs.nebius.com/kubernetes/node-groups/manage)
  - Terraform `reservation_policy` has AUTO, which falls back to PAYG — [Terraform: mk8s_v1_node_group](https://docs.nebius.com/terraform-provider/reference/data-sources/mk8s_v1_node_group)
  - A Nebius solution-library PR notes that a surge node above the reserved count spills into PAYG under AUTO, or stalls the rollout under STRICT, and doesn't move back without recreation — [nebius-solutions-library PR #1091](https://github.com/nebius/nebius-solutions-library/pull/1091)
- **Quota note:** default Compute quotas for B200 and H200 in us-central1 are zero for tenants created after December 1. Those tenants must request a quota increase; existing tenants are unaffected — [Nebius changelog](https://docs.nebius.com/changelog)
- **Node VMs and maintenance:** GPU auto-repair "stops and starts" the node's Compute VM to move it to healthy hardware, which shows nodes are VMs — [Nebius docs: Health checks](https://docs.nebius.com/kubernetes/maintenance/health-checks)
- **Bring Your Own Image (Aether 3.6):** Nebius describes BYOI as custom OS images "at the VM layer" for hardened or compliance-validated images — [Nebius blog: Aether 3.6](https://nebius.com/blog/posts/ai-cloud-aether-3-6)
- **Fixed node-group IPs:** a fixed set of public IPs can be created for a node group — [Nebius docs: defined IPs](https://docs.nebius.com/kubernetes/networking/defined-ips)

### Inferences
- Nebius exposes GB300 NVL72 in mk8s as rack-shaped node groups (18×4-GPU nodes) tied to an NVLink instance group. That is analogous to the full-rack NVL72 node-pool model at CoreWeave, but expressed through Nebius node groups. The constraints (no autoscaling or preemptible, zero surge) look similar in kind.
- The PAYG-fallback switch on reservations is a notable flexibility feature: nodes can still start when reserved capacity is short.

### Gaps
- **Bare metal:** I found no source describing bare-metal nodes in mk8s; all evidence points to VMs. I couldn't confirm whether Nebius sells bare metal outside mk8s.
- **GB200 NVL72 in mk8s:** no platform name (e.g., `gpu-gb200`) or preset for mk8s node groups was found.
- **CPU platforms:** names and presets for CPU-only platforms usable in node groups were not retrieved, beyond "Intel Ice Lake" and "AMD EPYC Genoa".
- **On-demand pricing:** current per-GPU-hour on-demand rates were not retrieved from a primary source.
- **GB300 access model:** a search summary claimed GB300 NVL72 is "available through the sales team only", but the exact source page could not be pinned.
- **BYOI scope:** whether BYOI applies to mk8s node images, not just standalone VMs, is unconfirmed.

## GPU software stack: drivers/presets, GPU Operator, Network Operator, InfiniBand, topology-aware scheduling, NCCL tests, MIG

### Takeaway
For GPU nodes, mk8s offers "driverfull" Ubuntu 24.04 boot images selected by a driver preset (e.g., `cuda12.8`, `cuda13.0`). Customers can instead run custom or driverless images with their own NVIDIA GPU Operator, and the Network Operator is needed in some cases such as B200. InfiniBand comes from attaching node groups to a "GPU cluster". IB topology is exposed as node labels for topology-aware scheduling. Nebius documents an NCCL test procedure. MIG is supported via GPU Operator settings in Nebius's Terraform solution, but I found no first-party mk8s MIG doc. For GB300, mk8s owns IMEX configuration.

### Cited Findings
- **Driver presets:** setting a driver preset such as `cuda13.0` makes mk8s use a boot image with GPU drivers preinstalled. Omitting it means you install drivers yourself. Changing the driver preset on an existing node group recreates all its nodes — [Nebius docs: Working with GPUs](https://docs.nebius.com/kubernetes/gpu/set-up)
- Community notes map `cuda12.8` to driver 570.x and `cuda13.0` to 580.x on Ubuntu 24.04. This is unverified against official docs — [community cert-prep notes](https://github.com/sanketsultan/nebius-cloud-ops-cert-prep/blob/master/notes/02-gpu-clusters.md)
- Clusters with custom images must install the NVIDIA GPU Operator. The NVIDIA Network Operator is required in some cases, e.g., with B200 GPUs — [Nebius docs: Working with GPUs](https://docs.nebius.com/kubernetes/gpu/set-up)
- The Nebius Applications catalog includes `nvidia-gpu-operator`, `nvidia-network-operator` and `nvidia-device-plugin` as installable apps — [nebius/nebius-k8s-applications](https://github.com/nebius/nebius-k8s-applications)
- **InfiniBand:**
  - Specify a GPU cluster when creating the node group to connect GPUs across nodes over InfiniBand — [Nebius docs: InfiniBand GPU clusters](https://docs.nebius.com/kubernetes/gpu/clusters)
  - B200/B300 nodes need a GPU cluster for IB, with a compatible platform and preset — [Nebius docs: NCCL tests](https://docs.nebius.com/kubernetes/gpu/nccl-test)
  - Only 8-GPU nodes can join an IB GPU cluster — [k8s-training README](https://github.com/nebius/nebius-solutions-library/blob/main/k8s-training/README.md)
- **Topology-aware scheduling:** Nebius exposes InfiniBand topology as node labels, so schedulers can place workloads on nodes that are physically closer in the network hierarchy — [Nebius docs: InfiniBand GPU clusters](https://docs.nebius.com/kubernetes/gpu/clusters)
- **NCCL tests:** there is a dedicated guide for running NCCL tests on mk8s with IB-connected GPUs (B200, B300 and others) — [Nebius docs: NCCL tests](https://docs.nebius.com/kubernetes/gpu/nccl-test)
- **k8s-training reference solution:** includes the GPU Operator ("automates NVIDIA driver and plugin deployment") and the Network Operator, with InfiniBand between hosts — [nebius-solutions-library](https://github.com/nebius/nebius-solutions-library)
- **GB300 drivers/IMEX:** GB300 nodes use "Ubuntu 24.04 driverfull images with the `cuda13.0` driver preset", and "IMEX configuration is owned by MK8s Node Infrastructure for driverfull GB300 node groups" — [k8s-training README](https://github.com/nebius/nebius-solutions-library/blob/main/k8s-training/README.md)
- **MIG:** the k8s-training solution exposes `mig_strategy` (`single`/`mixed`/`none`) and `mig_parted_config` (default `all-disabled`). GB300 rejects MIG — [k8s-training README](https://github.com/nebius/nebius-solutions-library/blob/main/k8s-training/README.md). The solution library also has a `gpu_slicing` directory — [nebius-solutions-library](https://github.com/nebius/nebius-solutions-library)
- **Soperator stack:** requires the NVIDIA GPU Operator (latest stable), Ubuntu 24.04 node images, CUDA >13.0 and NCCL ≥2.28, and recommends Cilium as a kube-proxy replacement — [nebius/soperator](https://github.com/nebius/soperator)

### Inferences
- Nebius supports both models: driver-in-image ("driverfull") node groups managed by the platform, and BYO via GPU Operator. The "MK8s Node Infrastructure" owning IMEX for GB300 suggests NVL72 multi-node NVLink is expected to run on Nebius-managed images.
- MIG and time-slicing appear to be customer-configured via GPU Operator settings, with Nebius Terraform templates helping. I found no evidence of a platform-native MIG toggle in the node-group API.

### Gaps
- No official table of driver presets per platform was retrieved (which `cuda*` presets apply to H100/H200/B200/B300/GB300 and which driver versions). Search noted that the Terraform reference and CLI examples differ on presets.
- Whether the GPU Operator or Network Operator is pre-installed and managed on driverfull node groups (vs. only drivers in the image) was not confirmed.
- The exact topology label names (e.g., IB switch tier labels) were not retrieved.
- There is no first-party doc on MIG for mk8s, and no confirmation of Kueue, Volcano or other gang-scheduler support beyond Volcano appearing as an installable app — [nebius-k8s-applications](https://github.com/nebius/nebius-k8s-applications).

## Reliability: node auto-repair, GPU health checks, automatic replacement, maintenance, node problem detection

### Takeaway
Auto-repair is on by default. Node Problem Detector runs on every node, and condition-driven repair replaces nodes with boot-disk I/O errors or prolonged NotReady status. GPU nodes get passive health checks every 5 minutes (nvidia-smi, dcgmi, dmesg; Xid and ECC; IB and NVLink). A failure sets `NebiusGPUError`, which triggers cordon → drain (up to 1 h) → VM stop/start onto healthy hardware. Active (stress-test) GPU checks are default in Soperator but only "preview on request" for mk8s. Repair can be disabled per condition.

### Cited Findings
- **Node Problem Detector:** mk8s runs NPD as a systemd service on each node by default — [Nebius docs: Health checks & automatic recovery](https://docs.nebius.com/kubernetes/maintenance/health-checks)
- **Boot disk:** an I/O error persisting for 30 s or more sets `NebiusBootDiskIOError`, and recovery replaces the node — [Nebius docs: Health checks](https://docs.nebius.com/kubernetes/maintenance/health-checks)
- **NotReady:** recovery runs when the Ready condition stays False for more than 5 minutes or Unknown for more than 15 minutes. It deletes the node and creates a healthy one with a different name — [Nebius docs: Health checks](https://docs.nebius.com/kubernetes/maintenance/health-checks)
- **GPU checks:** mk8s checks GPUs, InfiniBand and NVLink using nvidia-smi, dcgmi and dmesg, and checks for Xid errors and ECC memory problems. Each GPU check runs every five minutes, and `NebiusGPUError` is set to False when all pass — [Nebius docs: Health checks](https://docs.nebius.com/kubernetes/maintenance/health-checks)
- **GPU remediation flow:**
  - When `NebiusGPUError` is True, the service cordons the node, waits for GPU workloads to stop, drains it (up to one hour), then stops and starts the VM so the node moves to healthy hardware.
  - If the issue clears before the drain begins, the node is uncordoned instead.
  - [Nebius docs: Health checks](https://docs.nebius.com/kubernetes/maintenance/health-checks)
- **Opt-out:** auto-repair can be disabled per condition (e.g., `NebiusGPUError`, `NebiusBootDiskIOError`, NodeReady) via the node-group auto-repair spec, using the API, SDK or CLI — [Nebius docs: Enable/disable health checks](https://docs.nebius.com/kubernetes/maintenance/enable-disable-health-checks)
- **Auto-repair defaults:** auto-repair is enabled by default, and it keeps resources available when VMs are stopped by a maintenance event — [Nebius docs: Create/modify node groups](https://docs.nebius.com/kubernetes/node-groups/manage)
- **Passive vs active checks:** Nebius separates passive checks (continuous) from active checks (run during lifecycle events or idle periods). Active checks are "enabled by default on Soperator-based clusters and … available in preview mode for Managed Kubernetes environments upon request" — [Nebius blog: How we build reliable clusters](https://nebius.com/blog/posts/how-we-build-reliable-clusters)
- **Maintenance reason codes:** codes include `HW_GPU_XID_62` and `HW_GPU_NVLINK_DOWN`, viewable via `nebius compute maintenance list-active`. For `HW_NODE_OFFLINE`, affected VMs are force-migrated and "will experience an unexpected reboot" — [Nebius docs: Maintenance reasons](https://docs.nebius.com/compute/virtual-machines/maintenance-reasons)
- **Standalone VMs:** an autohealing option stops and starts a VM whenever maintenance is scheduled — [Nebius docs: Stop/start VMs](https://docs.nebius.com/compute/virtual-machines/stop-start)
- **Soperator health checks:**
  - Built-in health checks (DCGM diagnostics and NCCL tests) run on a schedule on each GPU worker. Workers that fail critical checks are marked for action. Managed Soperator uses the checks to auto-heal workers — [Nebius docs: Soperator worker health](https://docs.nebius.com/slurm-soperator/worker-nodes-health)
  - Soperator's `ActiveCheck` probes cover GPU, system, storage and network, and failed nodes are drained and replaced automatically — [nebius/soperator](https://github.com/nebius/soperator)
  - If Compute schedules maintenance during checks, Soperator stops the checks, then drains and recreates the node — [Nebius docs: Soperator worker health](https://docs.nebius.com/slurm-soperator/worker-nodes-health)

### Inferences
- mk8s GPU health handling is passive-by-default with automated remediation. The deeper active burn-in/diagnostic path is productized mainly in Soperator, so mk8s users wanting pre-job active validation must request the preview or roll their own.
- The documented remediation is "stop/start VM to move to a healthy host", not in-place repair, so workloads must tolerate a node restart.

### Gaps
- No evidence of live migration for GPU VMs. Maintenance appears to mean reboot or stop/start.
- I didn't find the full list of DCGM levels and tests used in mk8s passive checks, or whether IB link flaps are a distinct condition.
- I didn't find whether customers get advance notice windows for planned maintenance on mk8s nodes, or a maintenance-window setting.

## Autoscaling: cluster autoscaler, Karpenter, scale-to-zero, limits

### Takeaway
mk8s provides a built-in, node-group-based Cluster Autoscaler with min/max per node group, up to 100 nodes per group. I found no Karpenter support. Scale-down of GPU groups is enabled by running system add-ons (CoreDNS, Cilium) on a separate CPU node group. Scale-to-zero is implied by that guidance but not explicitly confirmed. NVL72/GB300 rack node groups are not autoscaled.

### Cited Findings
- **Cluster autoscaler:** it adds nodes when pods can't be scheduled and removes underutilized nodes — [Nebius docs: Autoscaling](https://docs.nebius.com/kubernetes/node-groups/autoscaling)
- **Configuration:**
  - Min and max are set with `--autoscaling-min-node-count` and `--autoscaling-max-node-count` (example 2–4), or with Min./Max. fields in the console.
  - Autoscaling for existing node groups can only be managed through the CLI.
  - The autoscaling flags are mutually exclusive with `--fixed-node-count`.
  - [Nebius docs: Autoscaling](https://docs.nebius.com/kubernetes/node-groups/autoscaling); [CLI: node-group create](https://docs.nebius.com/cli/reference/mk8s/v1alpha1/node-group/create)
- **Limit:** max 100 nodes per node group — [Nebius docs: Create/modify node groups](https://docs.nebius.com/kubernetes/node-groups/manage)
- **GPU scale-down guidance:** add a CPU node group with at least two nodes (or with autoscaling), so the GPU group can scale down; CoreDNS and Cilium can run on CPU nodes — [Nebius docs: Autoscaling](https://docs.nebius.com/kubernetes/node-groups/autoscaling)
- **Ownership:** a 2026 Nebius job posting says the mk8s Core team owns "cluster autoscaling" alongside the API, the service control plane and k8s add-ons — [Accel jobs: Nebius mk8s](https://jobs.accel.com/companies/nebius-2-a589082e-e06c-4189-b09d-dc098bbac70b/jobs/92106377-senior-tech-lead-backend-engineer-managed-kubernetes)
- **GB300:** Nebius's GB300 rack deployment path rejects autoscaling — [k8s-training README](https://github.com/nebius/nebius-solutions-library/blob/main/k8s-training/README.md)
- **Soperator ephemeral workers:** Soperator creates workers on demand and scales them down when idle — [nebius/soperator](https://github.com/nebius/soperator). It can release specific worker nodes back to a shared capacity block for reuse, e.g., by an inference k8s cluster — [Nebius docs: Ephemeral nodes in Soperator](https://docs.nebius.com/slurm-soperator/capacity/ephemeral-nodes)
- **Third-party restart behavior:** an article reports Nebius saying "Kubernetes restarts pre-emptible VMs in a cluster" — [btw.media](https://btw.media/en/nebius-gpu-spot-price-cost-cap-scheduling-rule)

### Inferences
- The autoscaler model is classic Cluster Autoscaler over predefined node groups, not just-in-time instance-type selection. This matters when comparing with providers that offer Karpenter-style provisioning.
- The CPU-node-group guidance strongly suggests GPU groups can scale to min 0, but no doc text explicitly says "min 0" was retrieved.

### Gaps
- Karpenter: no Nebius documentation or announcement mentions it. I treat it as not supported or not documented.
- No explicit confirmation of scale-to-zero (min=0) or of scale-from-zero for GPU node groups.
- No documented autoscaler tuning knobs (scale-down delay, expander) or per-cluster node limit beyond quotas.

## Storage & networking: shared filesystems, CSI, object storage, VPC/subnets, load balancers, CNI/network policy, egress

### Takeaway
- **CNI:** Cilium is the default CNI and network-policy engine (with CoreDNS), and kube-proxy is still listed as a node component.
- **Shared Filesystem:** mounted on nodes via virtiofs (cloud-init) and exposed to pods through a CSI driver or hostPath, with notable limitations.
- **Object Storage:** mounted via the Mountpoint-for-S3 CSI driver.
- **Load balancers:** public by default, internal via annotation, with reusable IP allocations and pools.
- **Endpoint access:** private endpoint plus CIDR allowlist on the public endpoint.

### Cited Findings
- **Default add-ons:** mk8s clusters have CoreDNS and Cilium installed by default — [Nebius docs: Networking add-ons](https://docs.nebius.com/kubernetes/networking/add-ons). The node-based components are kubelet, kube-proxy and Cilium; Cilium enforces which pods and services can reach each other — [Nebius docs: Components](https://docs.nebius.com/kubernetes/components)
- **NodeLocal DNSCache:** there is a guide for running it with the Cilium network policy controller — [Nebius docs: NodeLocal DNSCache](https://docs.nebius.com/kubernetes/networking/nodelocal-dns-cache)
- **Run:ai caveat:** the Run:ai integration guide notes eBPF networking can conflict with CoreDNS and cause DNS failures, and works around it with a custom CoreDNS ConfigMap — [Nebius docs: Run:ai](https://docs.nebius.com/3p-integrations/run-ai)
- **Load balancers:**
  - Load balancers get public IPs by default. The annotation `nebius.com/load-balancer-type: internal` gives a private IP.
  - `nebius.com/load-balancer-allocation-id` reuses an existing IP allocation, whose type must match.
  - `nebius.com/load-balancer-pool-id` selects a public IP pool.
  - [Nebius docs: Load balancers](https://docs.nebius.com/kubernetes/clusters/load-balancer)
- **Egress / fixed IPs:** a fixed set of public IPs can be created for a node group — [Nebius docs: defined IPs](https://docs.nebius.com/kubernetes/networking/defined-ips). Outbound VM traffic is listed as free by a third-party review — [getdeploying: Nebius](https://getdeploying.com/nebius)
- **Shared Filesystem via CSI:** a Compute shared filesystem is mounted on nodes with virtiofs via cloud-init in the node group, then exposed to Pods through a CSI driver as a persistent volume — [Nebius docs: Shared filesystems over CSI](https://docs.nebius.com/kubernetes/storage/filesystem-over-csi). SkyPilot describes Nebius auto-mounting the filesystem on node-group hosts and Pods reaching it via hostPath — [SkyPilot docs: Volumes](https://docs.skypilot.co/en/latest/reference/volumes.html)
- **CSI limitations** (Nebius solution library):
  - "FS should be mounted to all NodeGroups, because PV attachment to pod running on Node without FS will fail."
  - volumeMode Block is not supported.
  - FS size can't be updated via the API.
  - Filestore is enabled by default in the solution, mounted at `/mnt/filestore` or `/mnt/data`.
  - Terraform installs the CSI driver and can make its StorageClass the default.
  - [k8s-training README](https://github.com/nebius/nebius-solutions-library/blob/main/k8s-training/README.md)
- **Object Storage:** Mountpoint for Amazon S3 can mount Nebius Object Storage buckets, including on mk8s via the Mountpoint S3 CSI driver installed with Helm (PVs reference a bucket). ReadWriteMany requires contacting support — [Nebius docs: Mountpoint for S3](https://docs.nebius.com/object-storage/interfaces/mountpoint-s3)
- **ClusterMAX 3.0 storage finding:** testing found that concurrent directory creation from 128+ ranks sometimes returned EEXIST (inconsistent metadata visibility) on one storage tier. Nebius retuned the filesystem during the review, after which SemiAnalysis found it "sturdy and fast" — [Nebius blog: Platinum in ClusterMAX 3.0](https://nebius.com/blog/posts/nebius-platinum-clustermax-3-0); [SemiAnalysis ClusterMAX 3.0](https://newsletter.semianalysis.com/p/clustermax-30-the-industry-standard)
- **Endpoint access:** private endpoint reachable from the VPC. The public-endpoint CIDR allowlist is enforced via security groups — [Nebius docs: Access restriction](https://docs.nebius.com/kubernetes/networking/limit-access-to-public-endpoint). Node groups support VPC security groups — [Nebius changelog](https://docs.nebius.com/changelog)
- **Soperator recommendation:** Soperator recommends Cilium in kube-proxy-replacement mode — [nebius/soperator](https://github.com/nebius/soperator)

### Inferences
- Shared FS integration is functional but less "native" than a fully dynamic-provisioning CSI. The FS must be pre-attached to every node group and can't be resized via the API, which constrains elastic or multi-node-group designs.
- Nebius lists kube-proxy among node components while also shipping Cilium. That suggests default mk8s runs Cilium for policy and networking alongside kube-proxy, not in full kube-proxy-replacement mode. Soperator separately "recommends" replacement mode. Unconfirmed.

### Gaps
- Whether Cilium runs as a kube-proxy replacement on mk8s, and whether Hubble is exposed, is unconfirmed. A search extraction mentioned "Hubble and Cilium metrics are now shipped to Grafana and Prometheus … in Applications for Managed Kubernetes", but I could not pin the source page.
- I found no Nebius-native block-storage CSI details (Network SSD/HDD disks as PVs, snapshot support), dynamic shared-FS provisioning, or a native S3 CSI.
- I found no documentation of a NAT gateway or egress-gateway product for private nodes, and no detail on pod CIDR, service CIDR or IPv6.

## Ecosystem: Soperator, Applications/Helm marketplace, observability, Container Registry, IAM, Terraform Solution Library

### Takeaway
mk8s is surrounded by a wide Nebius-owned ecosystem:
- **Soperator:** Slurm on Kubernetes (Apache-2.0), also offered as Managed Soperator and Soperator Pro.
- **Applications catalog:** about 37 one-click Helm apps installed from the console, including GPU/Network Operators, JupyterHub, Ray, vLLM, Kubeflow, Volcano, NVIDIA OSMO, Run:ai, dstack and Argo CD, plus a "Custom Helm chart" app.
- **Observability:** a Nebius Observability Agent for Kubernetes with hosted Monitoring (Prometheus-compatible) and Logging (Loki-compatible) and Grafana.
- **Other:** Container Registry, OIDC/workload identity federation, and a Terraform solutions library (k8s-training, soperator, SkyPilot, Anyscale).

### Cited Findings
- **Soperator:**
  - An open-source Kubernetes operator that runs Slurm via a `SlurmCluster` CR.
  - Features: a shared root filesystem ("jail") across login and worker nodes; passive and active health checks with automatic drain and replace; preinstalled drivers, CUDA, NCCL, nccl-tests and Pyxis/Enroot; ephemeral autoscaled workers; InfiniBand-aware topology placement (tier-2 switch constraints); SSSD/LDAP/AD; Slurm accounting; Prometheus/Grafana/Loki.
  - License: Apache 2.0. The bundled Slurm build is `26.05.4-nebius-1`.
  - Requirements: K8s ≥1.35, Helm ≥3.14, Ubuntu 24.04 nodes.
  - It can run on other clouds (AWS, GCP, Azure, OCI, bare metal).
  - Nebius also offers Managed Soperator (console setup) and Soperator Pro (Nebius engineers install, tune and support).
  - [nebius/soperator](https://github.com/nebius/soperator); [Nebius: Slurm-on-Kubernetes solutions](https://nebius.com/services/soperator)
- **Applications catalog repo:** contains 37 app directories: apache-airflow, argo-project-argo-cd, clearml-agent, clickhouse, coder-server, comfy-ui, custom-helm, cvat-cvat, docker-deployment, dstack, flowise, generative-virtual-screening, grafana-and-prometheus, h2o-3, h2oai-h2ogpt, headlamp, jupyterhub, kubernetes-dashboard, litellm, loki, milvus-milvus, ml-flow, nebius-grafana, nebius-observability-agent, nvidia-device-plugin, nvidia-gpu-operator, nvidia-network-operator, nvidia-osmo, ollama, qdrant-qdrant, ray-cluster, ray-serve, stable-diffusion-webui, vllm, volcano-volcano, weaviate and others. You install them from the Applications tab of an mk8s cluster, or with Helm on any k8s — [nebius/nebius-k8s-applications](https://github.com/nebius/nebius-k8s-applications)
- **Custom Helm Chart app:** installs any Helm chart into a cluster without kubectl or the Helm CLI — [Nebius applications catalog](https://nebius.com/third-party-applications/vllm)
- **Kubeflow:** offered as an app; vendor listed as Aranui Solutions — [Nebius apps: Kubeflow](https://nebius.com/applications/kubeflow); [Nebius blog: Kubeflow on Marketplace](https://nebius.com/blog/posts/pre-training/kubeflow-is-now-available-on-marketplace)
- **JupyterHub:** with PyTorch and CUDA, deployable into mk8s via the Marketplace (older post) — [Nebius blog: JupyterHub](https://nebius.com/blog/posts/jupyterhub-released-on-marketplace)
- **Ray Serve and vLLM:** apps for k8s — [Nebius apps: Ray Serve](https://nebius.com/applications/ray-serve); [Nebius apps: vLLM](https://nebius.com/applications/vllm). A third-party article (about 4 months old) claimed the vLLM K8s add-on "doesn't seem to be an official Helm chart" — [Cloudthrill](https://cloudthrill.ca/explore-nebius-ai-cloud)
- **Run:ai:** open-source NVIDIA Run:ai is available in Nebius Applications — [Nebius blog: Run:ai on Applications](https://nebius.com/blog/posts/nvidia-run-ai-open-source-nebius-applications); [Nebius docs: Run:ai config](https://docs.nebius.com/3p-integrations/run-ai)
- **Observability:**
  - The Nebius Observability Agent for Kubernetes has its own changelog, with fixes in June and August 2026 (e.g., a metrics batching bug, and removal of a node-group label from cluster-wide control-plane metrics) — [Nebius docs: o11y agent changelog](https://docs.nebius.com/observability/resources/nebius-o11y-agent-changelog)
  - Metrics are readable through a Prometheus endpoint `https://read.monitoring.api.nebius.cloud/projects/<project_ID>/service-provider/prometheus` from Grafana — [Nebius docs: Grafana with Nebius metrics](https://docs.nebius.com/observability/metrics/grafana)
  - Logs are available for mk8s and its applications, viewable in Grafana via a Loki data source — [Nebius docs: Logs](https://docs.nebius.com/observability/logging)
  - Apps include "Grafana and Prometheus", "Grafana solution by Nebius" (integrated with Nebius Monitoring and Logging) and Loki (Promtail) — [Nebius apps: Grafana & Prometheus](https://nebius.com/applications/grafana-and-prometheus); [Nebius apps: Grafana solution by Nebius](https://nebius.com/applications/grafana-solution-by-nebius); [Nebius apps: Loki](https://nebius.com/applications/loki)
  - In the k8s-training solution, the defaults are `enable_nebius_o11y_agent=true`, `enable_grafana=true`, `enable_prometheus=false` and `enable_loki=false`. The default stack uses the Nebius o11y agent plus the Grafana solution — [k8s-training README](https://github.com/nebius/nebius-solutions-library/blob/main/k8s-training/README.md)
  - Nebius announced new metrics, Grafana dashboards and advanced logging for AI clusters — [Nebius blog: Improving AI cluster observability](https://nebius.com/blog/posts/improving-ai-cluster-observability)
- **Container Registry:** became publicly available in Q1 2025, with static-token support for CI/CD (e.g., Argo CD). The same post described open monitoring and logging as a preview at that time — [Nebius blog: Q1 2025 updates](https://nebius.com/blog/posts/q1-2025-cloud-updates). A doc covers pulling images from Container Registry for mk8s workloads — [Nebius docs: Container Registry images](https://docs.nebius.com/kubernetes/workloads/images-container-registry)
- **Solutions library:** includes:
  - k8s-training (GPU Operator, Network Operator, InfiniBand)
  - soperator
  - anyscale (Anyscale operator)
  - skypilot, and skypilot/multiregion (two mk8s clusters in different regions, one for training and one for serving)
  - wireguard and bastion
  - plus applications, compute-testing, gpu_slicing, nfs-server and other directories
  - [nebius/nebius-solutions-library](https://github.com/nebius/nebius-solutions-library)
- **Third-party integrations:** Seqera/Nextflow (Terraform repo) — [seqeralabs/seqera-nebius-kubernetes-tf](https://github.com/seqeralabs/seqera-nebius-kubernetes-tf). Saturn Cloud enterprise install on Nebius — [Saturn Cloud](https://saturncloud.io/docs/enterprise/nebius/)

### Inferences
- The Applications catalog is the main "managed add-on" mechanism. It is closer to a curated Helm marketplace than to provider-managed add-ons with lifecycle guarantees: Nebius notes the cluster "handles maintenance" only for some apps.
- Soperator is a differentiator. It runs Slurm on the same mk8s substrate, with ephemeral nodes able to hand capacity back to k8s inference clusters.

### Gaps
- I found no first-party statement on whether catalog apps (e.g., GPU Operator) are upgraded or supported by Nebius after install.
- I didn't find a "managed Prometheus" SLA or retention period, or whether DCGM GPU metrics are collected by default by the o11y agent.
- I found no Nebius-specific Kueue, KAI-scheduler or kube-scheduler topology plugin offering.
- Catalog pages for Kubeflow and JupyterHub are dated (some 2+ years old), and their current availability wasn't verified.

## Regions, quotas, pricing model for nodes

### Takeaway
mk8s is marked available in every Nebius region:
- **Public regions:** eu-north1 (Finland), eu-north2, eu-west1 and us-central1 (Kansas City).
- **Newer regions:** uk-south1, plus private regions eu-west2 (Paris) and us-north1 (Woodbury, MN).

The default quota is 2 clusters per region, and node counts are bounded by Compute quotas. Pricing: the control plane is free, and you pay for node VMs (per-GPU-hour on-demand, discounted commitments via capacity blocks, or preemptible spot), disks and storage.

### Cited Findings
- **Region coverage:** the regions page marks "Managed Service for Kubernetes ✓" across all regions, including uk-south1, eu-west2 and us-north1. eu-north1 is Finland and us-central1 is Kansas City, Missouri — [Nebius docs: Regions](https://docs.nebius.com/overview/regions)
- **New private regions:** us-north1 (Woodbury, Minnesota) and eu-west2 (Paris, France) are now available, and both support mk8s — [Nebius changelog](https://docs.nebius.com/changelog)
- **Quotas:**
  - Default 2 clusters per region.
  - Node counts are limited by the Compute "Total number of allocations" quota (default 1000). The docs' example: up to 498 nodes with private IPs and no HA control plane.
  - Kubernetes nodes count against Compute VM quotas.
  - [Nebius docs: mk8s quotas & constraints](https://docs.nebius.com/kubernetes/resources/quotas-limits); [Nebius docs: Compute quotas](https://docs.nebius.com/compute/resources/quotas-limits)
- **Storage quotas:** defaults for uk-south1, eu-west2 and us-north1 are 32 disks and 5 TiB of Network SSD — [Nebius docs: Compute quotas](https://docs.nebius.com/compute/resources/quotas-limits)
- **Capacity advisor:** reports GPU availability against your quotas, in the console or API (unofficial mirror of Nebius docs) — [Capacity advisor](https://nebius-ai-cloud.mintlify.app/compute/virtual-machines/capacity-advisor)
- **Pricing model:**
  - mk8s is "Free" — [Nebius prices](https://nebius.com/prices)
  - Compute pricing lists on-demand and preemptible per-GPU-hour rates per platform. The L40S AMD platform is only in eu-north1 — [Nebius docs: Compute pricing](https://docs.nebius.com/compute/resources/pricing)
  - Reserved capacity is billed through commitment-discount addenda tied to capacity block groups — [Nebius docs: Capacity block groups](https://docs.nebius.com/overview/limits/capacity-block-groups)
  - Spot prices have been dynamic since Oct 2026, as noted under Compute — [btw.media](https://btw.media/en/nebius-gpu-spot-price-cost-cap-scheduling-rule)

### Inferences
- The 2-cluster/region default quota and the 1000-allocation quota are soft limits that likely need tickets for large deployments. Nodes with public IPs consume more allocations, which is why the example uses private IPs.

### Gaps
- Current on-demand per-GPU-hour list prices (H100/H200/B200/B300/GB300) were not retrieved from a primary source.
- The full region list with exact GPU-platform availability per region beyond what is cited (e.g., eu-north2, eu-west1 platforms, and GB300 location) was not retrieved.
- The default GPU quotas per region were not retrieved.

## Notable announcements, limitations, customer/independent reports (2025–2026)

### Takeaway
2025–2026 brought:
- Blackwell and Blackwell Ultra (B200, B300, GB200/GB300 NVL72) into mk8s.
- Enterprise controls in Aether 3.6 (June 2026): API IP allowlists, BYOI and workload identity federation.
- NVLink-aware node groups (June 2026).
- K8s 1.35 as default, with 1.36 conformance (Sep 2026).
- A Platinum ClusterMAX 3.0 rating (Sep 2026), up from Gold in 2.0.

Key limitations: no auto-upgrades, immutable node-group platform, 100-node node-group cap, shared-FS CSI restrictions, active GPU checks only in preview for mk8s, and no Karpenter.

### Cited Findings
- **Timeline:**
  - **Q1 2025:** Container Registry publicly available. Monitoring and logging still in preview — [Nebius blog: Q1 2025](https://nebius.com/blog/posts/q1-2025-cloud-updates)
  - **Jun 11, 2025:** first GA of GB200 capacity in Europe — [Nebius newsroom](https://nebius.com/newsroom/nebius-delivers-first-nvidia-blackwell-general-availability-in-europe-brings-nvidia-ai-enterprise-to-nebius-ai-cloud)
  - **AI Cloud 3.1:** GB300 NVL72 and HGX B300 in customer use, with "transparent capacity management". Date not captured — [Nebius newsroom: AI Cloud 3.1](https://nebius.com/newsroom/nebius-ai-cloud-3-1-delivers-next-generation-nvidia-blackwell-ultra-compute-with-transparent-capacity-management-for-ai-at-scale)
  - **Mar 26, 2026:** Aether 3.5 introduced Serverless AI — [Business Wire](https://secure.businesswire.com/news/home/20260326483373/en/Nebius-AI-Cloud-3.5-Introduces-Serverless-AI-to-Give-Developers-Frictionless-Compute-for-Real-World-AI); [Nebius blog: Aether 3.5](https://nebius.com/blog/posts/ai-cloud-aether-3-5)
  - **Jun 24, 2026:** Aether 3.6 added mk8s API access control (IP ranges), BYOI, Workload Identity Federation and K8s 1.34 — [Nebius newsroom: 3.6](https://nebius.com/newsroom/nebius-ai-cloud-3-6-strengthens-developer-experience-and-governance-for-production-operations); [Nebius blog: Aether 3.6](https://nebius.com/blog/posts/ai-cloud-aether-3-6)
  - **2026-06-25:** CLI flag for node groups with NVLink instance-group templates — [Nebius CLI release notes](https://docs.nebius.com/cli/release-notes)
  - **Sep 11, 2026:** v1.36 conformance merged — [cncf/k8s-conformance PR #4392](https://github.com/cncf/k8s-conformance/pull/4392)
  - **Oct 1, 2026:** Q3 release notes highlight CNCF conformance — [Nebius release notes Q3 2026](https://nebius.com/release-notes/posts/q3-2026)
- **ClusterMAX 3.0 (published Sep 2026):** Nebius rated Platinum (top tier, alongside CoreWeave), up from Gold in ClusterMAX 2.0. The report highlighted topology-aware Slurm, monitoring dashboards and Soperator, and said Nebius had become an industry leader able to charge premium prices — [Nebius blog: Platinum in ClusterMAX 3.0](https://nebius.com/blog/posts/nebius-platinum-clustermax-3-0) (vendor source); [SemiAnalysis ClusterMAX 3.0](https://newsletter.semianalysis.com/p/clustermax-30-the-industry-standard); [Blockspace](https://blockspace.media/insight/clustermax-3-gpu-cloud-rankings-coreweave-nebius/)
  - Conflict: the clustermax.ai review page is still titled "Nebius Review 2026: Gold Tier GPU Cloud", probably reflecting 2.0 — [clustermax.ai Nebius review](https://www.clustermax.ai/cloudreview/nebius)
- **Q3 2026 positioning:** Nebius frames CNCF conformance as letting customers keep familiar tools and stay portable — [Nebius blog: Q3 2026 updates](https://nebius.com/blog/posts/compute-cloud-platform-q3-2026)
- **Documented limitations:**
  - No automatic upgrades, and a non-HA control plane goes down during upgrade — [Nebius docs: manage versions](https://docs.nebius.com/kubernetes/manage-versions)
  - Node-group platform, preset and GPU cluster are immutable — [Nebius docs: node groups](https://docs.nebius.com/kubernetes/node-groups/manage)
  - Max 100 nodes per node group — same source
  - Autoscaling on existing groups is CLI-only — [Nebius docs: Autoscaling](https://docs.nebius.com/kubernetes/node-groups/autoscaling)
  - No reservations for preemptible or CPU groups — [Nebius docs: Reservations](https://docs.nebius.com/kubernetes/node-groups/reservations)
  - Shared-FS CSI must be mounted on all node groups, has no Block mode and can't be resized via the API — [k8s-training README](https://github.com/nebius/nebius-solutions-library/blob/main/k8s-training/README.md)
  - Object Storage RWX needs support — [Nebius docs: Mountpoint S3](https://docs.nebius.com/object-storage/interfaces/mountpoint-s3)
  - Active GPU checks are preview-on-request for mk8s — [Nebius blog: reliable clusters](https://nebius.com/blog/posts/how-we-build-reliable-clusters)
  - Changing the driver preset recreates all nodes — [Nebius docs: GPUs](https://docs.nebius.com/kubernetes/gpu/set-up)
- **Customer and integration evidence:** Seqera (Nextflow) — [seqeralabs repo](https://github.com/seqeralabs/seqera-nebius-kubernetes-tf). Saturn Cloud — [Saturn Cloud docs](https://saturncloud.io/docs/enterprise/nebius/). SkyPilot volumes support for Nebius — [SkyPilot docs](https://docs.skypilot.co/en/latest/reference/volumes.html)

### Inferences
- The 2026 trajectory is enterprise hardening: access control, BYOI, identity federation and conformance. Rack-scale Blackwell Ultra (GB300 NVL72) arrives as NVLink-aware node groups. The biggest gaps against an AI-specialist managed k8s like CKS are likely:
  - managed or automatic upgrades;
  - active health checks by default in mk8s (vs. Soperator);
  - Karpenter-style provisioning and bare-metal nodes.
  These should be verified on the CKS side.

### Gaps
- I found no independent customer testimonials specific to mk8s (as opposed to Nebius overall) from 2025–2026.
- I found no ClusterMAX 3.0 Kubernetes-specific sub-score breakdown for Nebius.
- The full Q3 2026 release notes content (beyond conformance) was not retrieved, so other Q3 mk8s features may be missing.
