# CoreWeave Kubernetes Service (CKS): product features for AI/GPU workloads (as of Oct 2026)

> Method note: docs.coreweave.com, coreweave.com, clustermax.ai and newsletter.semianalysis.com were unreachable from this
> research environment (egress policy denial / DNS failure). Findings below come from search-engine extracts of those
> pages (URLs cited are the original pages), plus third-party sources. Exact wording and numbers should be spot-checked
> on the live pages before publication. Dates are given wherever a source exposed one.

## 1. Control plane: versions, upgrades, HA, SLA, pricing, endpoints, audit logging, OIDC/SSO

### Takeaway
CKS is a fully managed, free control plane (CoreWeave runs API server, etcd, scheduler, controllers out-of-band; no customer
control-plane nodes since July 2025) supporting the three most recent Kubernetes minors (currently v1.34–v1.36), with
one-minor-at-a-time upgrades, public-by-default API endpoints (private via Support + Tailscale/Traefik/Direct Connect),
control-plane audit logs, OIDC user auth and per-cluster OIDC workload identity. No published control-plane HA design or
uptime SLA was found.

### Cited Findings
- **Version policy:** CKS supports the three most recent Kubernetes versions; the current matrix lists v1.34, v1.35, v1.36, with v1.36 the default for new clusters; v1.29–v1.33 are listed as deprecated, with support limited by the upstream support period — [Cluster components / Kubernetes versions](https://docs.coreweave.com/products/cks/reference/cluster-components)
- **Version conflict:** an earlier changelog says "v1.35 is now the default for new CKS clusters" and an older one that v1.34 was the default; the reference matrix (v1.36 default) appears newer — [Changelog](https://docs.coreweave.com/changelog); [Cluster components](https://docs.coreweave.com/products/cks/reference/cluster-components)
- **Version history:** v1.32 became available June 13, 2025 — [Release note 2025-06-13](https://docs.coreweave.com/docs/changelog/release-notes/2025-06-13-cks-kubernetes-v1-32); a v1.33 release note also exists — [cks-kubernetes-v1-33](https://docs.coreweave.com/changelog/release-notes/cks-kubernetes-v1-33)
- **Upgrade rules:** upgrades go one minor version at a time (no skipping); downgrades are not supported; upgrades are blocked if the cluster uses APIs deprecated in the target version; v1.35+ requires all Nodes on cgroup v2 — [Upgrade Kubernetes](https://docs.coreweave.com/products/cks/clusters/upgrade-kubernetes)
- **Two-step upgrade:** control plane is upgraded first; Nodes are NOT automatically updated and must be rebooted afterwards (docs recommend rebooting as soon as possible) — [Upgrade Kubernetes](https://docs.coreweave.com/products/cks/clusters/upgrade-kubernetes)
- **Upgrades are customer-initiated** (user triggers the upgrade) — inferred from upgrade docs; CoreWeave also published a July 31, 2025 note on CKS upgrades — [2025-07-31 CKS upgrade note](https://docs.coreweave.com/docs/changelog/release-notes/2025-07-31-cks-upgrade-and-telecaster)
- **Managed scope:** "CoreWeave owns the API server, etcd, the scheduler, and the controllers that keep the cluster healthy" — [What CoreWeave manages](https://docs.coreweave.com/support/what-coreweave-manages)
- **No control-plane node pool (since July 7, 2025):** new CKS clusters no longer provision a `cpu-control-plane` Node Pool; managed components moved "out-of-band" to the CKS control plane for faster, more reliable provisioning; older clusters keep theirs and Support migrates per customer — [Release note: Control Plane Node Pools (July 7, 2025)](https://docs.coreweave.com/docs/changelog/release-notes/control-plane-node-pools)
- **Cilium operator and CoreDNS run in the managed control plane**, Cilium agent on customer nodes — [Cluster components](https://docs.coreweave.com/products/cks/reference/cluster-components)
- **Control plane pricing:** official pricing page lists the CKS control plane as "Free"; SUNK scheduler as "Free*" (excluding CPU resources needed to run SUNK) — [CoreWeave pricing](https://www.coreweave.com/pricing); corroborated by [usagepricing.com](https://www.usagepricing.com/blueprint/coreweave)
- **No billing for CoreWeave-managed control-plane namespaces' jobs** — [Workload scheduling on CKS](https://docs.coreweave.com/products/cks/clusters/scheduling/workload-scheduling)
- **SLA:** the only official SLA found is for AI Object Storage (≥99.9% uptime target); no CKS control-plane SLA found. A third-party review cites 99.9% (multi-region) / 99% (single region) monthly compute uptime objectives, unverified — [AI Object Storage SLA](https://docs.coreweave.com/policies/terms-of-service/coreweave-ai-object-storage-policy); [whtop review (unverified)](https://www.whtop.com/review/coreweave.com)
- **API endpoint exposure:** self-service clusters are public by default (Kubernetes API reachable over the internet); self-service creation supports public clusters only; private clusters (no public endpoint) require contacting Support — [Introduction to authentication and access control](https://docs.coreweave.com/products/cks/auth-access/introduction); [About networking](https://docs.coreweave.com/products/networking)
- **Private access paths:** Tailscale VPN (CKS runs a proxy that joins the customer tailnet and forwards to the API server; proxy authenticates via workload identity federation with the per-cluster OIDC issuer) or Traefik ingress, often paired with Direct Connect — [Access a CKS cluster through Tailscale VPN](https://docs.coreweave.com/products/cks/auth-access/tailscale-vpn)
- **Managed vs unmanaged endpoints:** each public cluster exposes a managed endpoint and an unmanaged endpoint (`api.` hostname prefix); "Unmanaged auth API" announced July 7, 2025; Managed Auth is required for new clusters/VPCs and recommended — [Unmanaged auth release note](https://docs.coreweave.com/release-notes/unmanaged-auth); [Intro to unmanaged authentication](https://docs.coreweave.com/products/cks/auth-access/unmanaged-auth/introduction)
- **Audit logging:** CKS grants access to the cluster API server enabling Control Plane audit logs; Kubernetes and Teleport audit logs can be forwarded automatically via CoreWeave Telemetry Relay; separate Super Regional Grafana data sources exist for CKS audit logs — [CKS introduction](https://docs.coreweave.com/products/cks); [Changelog](https://docs.coreweave.com/changelog)
- **OIDC for users:** cluster creation flow can enable OIDC (Issuer URL and Client ID required); users/service accounts authenticate with OIDC tokens mapped to Kubernetes RBAC (e.g., Okta) for namespace-level access — [Create a CKS cluster](https://docs.coreweave.com/products/cks/clusters/create); [Security architecture](https://docs.coreweave.com/security/architecture)
- **OIDC Workload Identity Federation (Nov 20, 2025):** every CKS cluster acts as an OIDC IdP with a discovery URL so workloads can authenticate to external services without stored credentials — [Release note: OIDC Workload Identity Federation](https://docs.coreweave.com/changelog/release-notes/oidc-workload-identity-federation)
- **IAM / SSO:** IAM Access Policies (Nov 20, 2025) are default-deny and govern Console actions incl. SAML SSO setup, CKS clusters and VPCs; product roles "CKS Admin" (create/update/delete clusters and VPC resources) and "CKS Viewer" (read-only); SAML SSO covers the Cloud Console only; in-cluster permissions are Kubernetes RBAC; legacy groups (admin/read/write/metrics/billing_viewer) superseded — [IAM Access Policies release note](https://docs.coreweave.com/release-notes/iam-access-policies); [IAM roles](https://docs.coreweave.com/security/iam/access-policies/roles); [Legacy user permissions](https://docs.coreweave.com/docs/products/cks/auth-access/managed-auth/permissions)
- **Support Access Management (Mar 26, 2026):** CoreWeave staff access to a CKS environment requires customer approval of an access request in the Cloud Console, with audit logs — [Release note: Support Access Management](https://docs.coreweave.com/release-notes/support-access-management)
- **IaC:** CKS API and Terraform provider released Feb 21, 2025 (`coreweave_cks_cluster` resource/data source) — [CKS API and Terraform provider release note](https://docs.coreweave.com/release-notes/cks-api-terraform); [coreweave_cks_cluster resource](https://docs.coreweave.com/platform/terraform/resources/cks_cluster)

### Inferences
- Because Nodes are not auto-upgraded and node reboot is required after a control-plane upgrade, upgrade operations on bare-metal GPU fleets are a coordinated customer task (likely using safe reboots that wait for idle nodes; see section 4).
- Free control plane plus no customer-visible control-plane nodes (post July 2025) puts CKS in line with managed-control-plane offerings; the absence of a published control-plane SLA is a notable comparison gap vs providers that publish one.

### Gaps
- No documentation found on control-plane HA topology (multi-replica API server / etcd quorum across zones) or a control-plane uptime SLA.
- No documentation found on source-IP allowlisting for the public API endpoint.
- Exact upgrade cadence/forced-upgrade policy when a version reaches end-of-support was not found (only "limited by upstream support period").
- Could not directly read docs pages; version default (v1.35 vs v1.36) should be confirmed live.

## 2. Compute: bare metal, GPU/CPU types, capacity models, allocation to node pools

### Takeaway
CKS runs Kubernetes directly on bare-metal nodes (no hypervisor, no VMs), offering NVIDIA H100, H200, B200, B300, GB200 NVL72,
GB300 NVL72 (InfiniBand and Spectrum-X RoCE variants), RTX PRO 6000 Blackwell, and (limited availability since Sept 30, 2026)
Vera Rubin NVL72. Capacity is bought via Reservations, Flex Reservations, On-Demand and Spot (Spot Node Pools since Mar 10, 2026)
and consumed through Node Pools, with quota tracked per org × instance type × compute class × zone.

### Cited Findings
- **Bare metal, no hypervisor:** "CKS runs Kubernetes directly on bare metal Nodes, without a hypervisor. Customer clusters don't run Virtual Machines"; product page says the hypervisor layer has been removed — [CKS introduction](https://docs.coreweave.com/products/cks); [CKS product page](https://www.coreweave.com/products/coreweave-kubernetes-service)
- **"Infra Control" umbrella:** CoreWeave groups Kubernetes, storage and networking products under "Infra Control"; CKS "runs bare-metal clusters on CoreWeave's GPU and CPU fleet" — [Infra Control docs](https://docs.coreweave.com/products/infra-control-home); Q1 2026 earnings deck frames "Infrastructure Control" as AI-native orchestration and bare-metal control — [Q1 2026 earnings presentation (May 7, 2026)](https://s205.q4cdn.com/133937190/files/doc_financials/2026/q1/CoreWeave-1Q26-Earnings-Presentation.pdf)
- **GPU instance catalog (current "About GPU instances" page):** Vera Rubin NVL72 (Spectrum-X RoCE), GB300 NVL72 (Quantum-X InfiniBand and Spectrum-X RoCE variants), GB200 NVL72, B300, B200, H200, H100, RTX PRO 6000 Blackwell Server Edition (High Memory and Standard Memory) — [About GPU instances](https://docs.coreweave.com/platform/instances/gpu-instances)
- **Instance specs:** GB300 NVL72 node = 4 GPUs (279 GB each), 144 vCPU, 960 GB RAM (two GB300 superchips); B200 (IB) = 8 GPUs ×180 GB, 128 vCPU, 2048 GB RAM; H200 (IB) = 8 GPUs ×141 GB, 128 vCPU, 2048 GB RAM; RTX PRO 6000 High Memory = 8 GPUs ×96 GB, 128 vCPU, 1024 GB RAM; Standard Memory variant 512 GB RAM, only in US-EAST-15A — [About GPU instances](https://docs.coreweave.com/platform/instances/gpu-instances); [GB300 NVL72 Quantum-X IB](https://docs.coreweave.com/platform/instances/gpu/gb300-4x); [GB200 NVL72 instances](https://docs.coreweave.com/platform/instances/gpu/gb200-4x)
- **NVL72 rack = 18 nodes:** each GB200/GB300 NVL72 system corresponds to a Slurm Block of 18 Nodes in the same NVLink domain — [Topology and block scheduling in Slurm](https://docs.coreweave.com/products/sunk/optimize_workloads/topology-scheduling)
- **GB300 NVL72 launch:** Aug 19, 2025 — [Release note: GB300 NVL72-powered instances](https://docs.coreweave.com/release-notes/gb300-nvl72)
- **Vera Rubin timeline (vendor claims):** first NVL72 bring-up June 1, 2026; multi-rack cluster Sept 16, 2026 (Mission Control "Rack LifeCycle Controller" automates rack setup: hardware detection, firmware, validation, power/cooling); limited availability Sept 30, 2026 with Cognition as first production customer (4.8× token throughput claim) — [Bring-up news](https://www.coreweave.com/news/coreweave-completes-industry-first-bring-up-of-nvidia-vera-rubin-nvl72); [Multi-rack news](https://www.coreweave.com/news/coreweave-brings-up-multi-rack-nvidia-vera-rubin-nvl72-cluster); [Cognition blog](https://www.coreweave.com/blog/cognition-becomes-first-customer-for-nvidia-vera-rubin-nvl72-on-coreweave-cloud); [Network World](https://www.networkworld.com/article/4229739/coreweave-brings-nvidia-vera-rubin-ai-tools-to-its-cloud-services.html)
- **CPU nodes:** CKS runs on GPU and CPU fleet; LOTA runs on every GPU and CPU node — [Infra Control](https://docs.coreweave.com/products/infra-control-home); [LOTA](https://docs.coreweave.com/products/storage/object-storage/improving-performance/about-lota). Vera CPU racks (128 Vera CPUs / 11,264 cores per rack) announced Sept 30, 2026 for agent/sandbox/RL workloads — [Vera CPU blog](https://www.coreweave.com/blog/nvidia-vera-cpu-is-coming-to-coreweave-pack-in-more-agents)
- **Capacity plans:** Reservations (fixed-term, guaranteed), Flex Reservations (guaranteed peaks), On-Demand, Spot (interruptible, best effort, no commitment); usage applied reserved → Flex → On-Demand overage; Spot billed separately at spot rate per SKU/region — [About capacity plans](https://docs.coreweave.com/platform/capacity-plans); [Flexible Capacity Plans press release (2026)](https://investors.coreweave.com/news/news-details/2026/CoreWeave-Introduces-Flexible-Capacity-Plans-to-Accelerate-AI-Innovation/default.aspx)
- **Spot Node Pools (Mar 10, 2026):** created with Spot compute class; same hardware/performance as standard pools; on reclamation the node is cordoned immediately and jobs get up to 7 minutes to stop/save state; pool stays active if fully preempted and scales back when capacity returns; no capacity guarantee — [Release note: Spot Node Pools](https://docs.coreweave.com/release-notes/spot-node-pools); [Spot Node Pools](https://docs.coreweave.com/platform/capacity-plans/spot-node-pools); [Spot Services Terms](https://docs.coreweave.com/policies/spot-tos)
- **computeClass doc inconsistency:** Node Pool reference table shows only `default` (Reserved and On-Demand), while Spot docs say `computeClass: Spot` — [Node Pool reference](https://docs.coreweave.com/products/cks/reference/node-pool); [Spot Node Pools](https://docs.coreweave.com/platform/capacity-plans/spot-node-pools)
- **Quotas:** per organization, per instance type, per compute class, per zone; default and spot pools have separate quota lines; when target nodes exceed quota, the Node Pool is filled up to the allowed quota; cluster creation requires quota in the zone — [Resource quotas](https://docs.coreweave.com/products/cks/clusters/quotas)
- **Capacity Finder API** checks availability per instance type and zone — [Get capacity availability](https://docs.coreweave.com/platform/capacity-plans/capacity-finder-api/capacityservice/get-capacity-availability)
- **Busy reservations:** nodes stuck "busy" inside a Reservation are re-evaluated after 30 minutes — [Changelog](https://docs.coreweave.com/release-notes/changelog)
- **Pricing examples:** pricing page lists HGX B300 spot at $35.84/hr (NA) and $36.70/hr (Europe rate card); GB300 NVL72 has no spot or self-serve on-demand price (sales-led) — [CoreWeave pricing](https://www.coreweave.com/pricing) (via search extract); third-party: [Spheron](https://www.spheron.network/blog/coreweave-gpu-pricing-2026/), [CloudZero](https://www.cloudzero.com/blog/coreweave-pricing/)
- **Rental terms (older critique):** SemiAnalysis previously noted CoreWeave "rarely accept[s] short-term rentals" — most capacity goes to large long-term tenants — [ClusterMAX rating system (original)](https://newsletter.semianalysis.com/p/the-gpu-cloud-clustermax-rating-system-how-to-rent-gpus)

### Inferences
- Node Pools are the unit of capacity allocation: a pool targets one instance type in one zone and draws from the quota line for (instance type, compute class, zone); Reservation/Flex/On-Demand are billing constructs layered on `default`-class pools, while Spot is a distinct compute class.
- The 2026 introduction of Spot Node Pools and Flex Reservations moves CoreWeave toward self-serve elasticity, though flagship rack-scale SKUs (GB300) remain sales-led.

### Gaps
- No official list of CPU-only instance types/specs was found.
- On-demand per-GPU prices for H100/H200/B200 were not verified from the official page in this session.
- Exact unit of the B300 spot price (per 8-GPU instance-hour presumed) not confirmed.

## 3. GPU stack: drivers, operators, InfiniBand/NVLink, topology-aware scheduling, MIG

### Takeaway
Clusters ship with GPU drivers, a CoreWeave-managed NVIDIA GPU Operator, DCGM, Multus for the secondary InfiniBand interface,
and rich topology labels (`ds.coreweave.com/nvlink.domain`, `ib.coreweave.cloud/{fabric,leafgroup,superpod}`,
`node.coreweave.cloud/rack`) consumed by a CoreWeave-packaged Kueue with Topology-Aware Scheduling enabled. MIG support is
undocumented.

### Cited Findings
- **Pre-installed stack:** CKS clusters ship with network and storage interfaces, GPU drivers, Slurm-on-Kubernetes and observability plug-ins already set up — [CKS product page](https://www.coreweave.com/products/coreweave-kubernetes-service)
- **GPU Operator is platform-managed:** CoreWeave manages the NVIDIA GPU Operator; docs warn against installing it yourself (conflicts with the platform deployment; unsupported) — [Introduction to clusters](https://docs.coreweave.com/products/cks/clusters/introduction)
- **DCGM pre-installed on all servers**, exposing chip-level metrics — [Hardware observability](https://docs.coreweave.com/platform/fleet-management/hardware-observability); [Cluster components](https://docs.coreweave.com/products/cks/reference/cluster-components)
- **Multus CNI** handles the secondary InfiniBand interface; component list also includes konnectivity-agent, MetalLB (replaced by Cilium LB IPAM on new clusters) — [Cluster components](https://docs.coreweave.com/products/cks/reference/cluster-components)
- **NVLink domain label:** all Nodes labeled `ds.coreweave.com/nvlink.domain`; on GB200/GB300 NVL72 the value is a globally unique rack ID (e.g., `DH4-016-US-EAST-02A`); `node.coreweave.cloud/rack` targets a physical rack — [About NVL72-powered instances](https://docs.coreweave.com/platform/instances/nvl72); [Workload scheduling on CKS](https://docs.coreweave.com/products/cks/clusters/scheduling/workload-scheduling)
- **InfiniBand labels:** `ib.coreweave.cloud/fabric`, `ib.coreweave.cloud/leafgroup`, `ib.coreweave.cloud/superpod` usable in Pod affinity — [InfiniBand labels](https://docs.coreweave.com/docs/products/networking/hpc-interconnect/infiniband-labels)
- **Kueue TAS:** CKS supports Kueue by default; install via CoreWeave's `coreweave/cks-kueue` chart (upstream admission webhooks can block system Pods otherwise); TopologyAwareScheduling enabled by default; docs suggest a `multinode-nvlink-ib` topology for rack-scale NVLink pools; a topology referencing labels not present on Nodes makes Kueue fail to admit workloads — [Kueue (CoreWeave Charts)](https://docs.coreweave.com/products/cks/clusters/coreweave-charts/kueue)
- **Kueue Grafana dashboard** exists — [Kueue Scheduling metrics](https://docs.coreweave.com/docs/observability/managed-grafana/cks/kueue-metrics)
- **Custom schedulers supported;** setting `.spec.nodeName` bypasses the scheduler, Kueue and TAS — [Custom schedulers and third-party integrations](https://docs.coreweave.com/products/cks/clusters/scheduling/custom-schedulers)
- **Slurm topology:** SUNK uses the Topology/Block plugin; each NVL72 = Block of 18 nodes; docs recommend `--segment=1`; SUNK-scheduled pods ignore Pod affinity/anti-affinity/topology spread (node affinity still applies); page now covers Vera Rubin — [Topology and block scheduling in Slurm](https://docs.coreweave.com/products/sunk/optimize_workloads/topology-scheduling)
- **Vera Rubin:** CKS uses topology-aware orchestration to place workloads within NVLink domains — [Vera Rubin deep dive](https://www.coreweave.com/blog/a-deep-dive-on-coreweave-innovations-for-nvidia-vera-rubin-nvl72)
- **Fabrics:** GB300 offered on both Quantum-X InfiniBand and Spectrum-X RoCE; Vera Rubin on Spectrum-X RoCE — [About GPU instances](https://docs.coreweave.com/platform/instances/gpu-instances); multi-plane network fabric blog — [5 lessons from a multi-plane network fabric](https://www.coreweave.com/blog/5-lessons-from-building-a-multi-plane-network-fabric-for-agentic-ai)
- **MIG:** no CoreWeave doc found; a third-party review notes customers use whole 8-GPU (or 4-GPU NVL72) machines so virtualizing into smaller GPU slices is uncommon — [clustermax.ai CoreWeave review (third-party)](https://www.clustermax.ai/cloudreview/coreweave)
- **Security trade-off:** ClusterMAX 2.0 said host-to-BMC access is disabled, which is "too limiting for a lot of power users" — [ClusterMAX 2.0](https://newsletter.semianalysis.com/p/clustermax-20-the-industry-standard)

### Inferences
- Because the GPU Operator is CoreWeave-managed, driver versions and GPU Operator configuration (e.g., MIG strategy) are platform-controlled rather than customer-tunable.
- The label taxonomy (NVLink domain → rack → IB leafgroup → superpod → fabric) maps directly to Kueue TAS topology levels, giving out-of-the-box topology-aware gang placement for Kubernetes jobs.

### Gaps
- No source on the NVIDIA Network Operator specifically (Multus + IB is documented, Network Operator is not).
- No source on MIG support/configuration on CKS.
- Driver/CUDA version policy and customer control over driver versions not found.

## 4. Reliability: Mission Control, node lifecycle, health checks, auto-replacement, visibility, telemetry

### Takeaway
Reliability is CoreWeave's core differentiator: Mission Control's Node Lifecycle Controller combines hourly active HPC
Verification on idle nodes, continuous passive (DCGM/log) monitoring and IB validation, then automatically reboots in place or
removes-and-replaces nodes (optionally pre-provisioning replacements via Node Pool "prefill"). Lifecycle state is exposed in
node labels/conditions, Node Pool events/status, and W&B run views. SemiAnalysis rated CoreWeave Platinum in ClusterMAX 1.0,
2.0 (Nov 2025) and 3.0 (Sept 23, 2026; now shared with Nebius).

### Cited Findings
- **Detection:** passive monitoring collects in-band and out-of-band telemetry and watches logs while workloads run; active checks (e.g., GPU burn, NCCL tests) mainly at bring-up or on idle production nodes — [Day 2+](https://docs.coreweave.com/platform/fleet-management/node-lifecycle/day2); [Node lifecycle blog](https://www.coreweave.com/blog/what-is-node-lifecycle-management-ml-training-and-inference)
- **HPC Verification:** periodic runs load every GPU on a node for 20–30 minutes, once per hour, only on idle Nodes (`hpc-verification` component) — [Day 2+](https://docs.coreweave.com/platform/fleet-management/node-lifecycle/day2); [Cluster components](https://docs.coreweave.com/products/cks/reference/cluster-components)
- **Alert types:** HPC Verification raises alerts for NVLink, NVFabric, memory bandwidth and non-determinism failures, among others — [Hardware observability](https://docs.coreweave.com/platform/fleet-management/hardware-observability)
- **Passive frequency:** SemiAnalysis says CoreWeave runs passive checks "every couple of seconds" and automatically drains/fixes unhealthy nodes — [ClusterMAX rating system](https://newsletter.semianalysis.com/p/the-gpu-cloud-clustermax-rating-system-how-to-rent-gpus)
- **Remediation categories:** "solved by reboot" (node reboots in-cluster and returns) vs "needs triage" (node leaves the cluster for investigation/repair/vendor RMA and CKS delivers a replacement automatically) — [Node lifecycle](https://docs.coreweave.com/platform/fleet-management/node-lifecycle); [Node state transitions](https://docs.coreweave.com/platform/fleet-management/node-lifecycle/state-transitions)
- **States:** production, production-reboot, production-collect-reboot (diagnostics then reboot), production-powercycle, production-reboot-fail; transitions use `node.coreweave.cloud/pending-state` label and `PendingPhaseState` Node condition; transitions wait until no active workload pods and evict interruptible pods first; `cwic node get` shows both — [Node state transitions](https://docs.coreweave.com/platform/fleet-management/node-lifecycle/state-transitions)
- **Cordoning:** NeedsTriage nodes get cordoned with a NoSchedule taint; CoreWeave automation can clear a plain `kubectl cordon`, so use a custom taint for long-term isolation — [Node cordoning](https://docs.coreweave.com/products/cks/nodes/cordon)
- **CWActive condition:** True when node has active workloads (incl. Pending and gracefully-interruptible pods until terminated); use ACTIVE column, not READY — [Reboot Nodes](https://docs.coreweave.com/products/cks/nodes/reboot); [Manage Node Pools](https://docs.coreweave.com/products/cks/nodes/manage)
- **Safe vs force reboot:** safe reboot waits until node is idle; force is immediate — [Reboot Nodes](https://docs.coreweave.com/products/cks/nodes/reboot)
- **Prefill:** opt-in (off by default); provisions replacement nodes before draining nodes marked for triage; requires autoscaling off and `default` compute class (not Spot) — [Node Pool reference](https://docs.coreweave.com/products/cks/reference/node-pool)
- **Return to fleet:** failed nodes are re-validated; full onboarding suite takes roughly 48 hours — [Day 2+](https://docs.coreweave.com/platform/fleet-management/node-lifecycle/day2)
- **Mission Control marketing:** Node Lifecycle Controller continuously monitors nodes and replaces them when thresholds are met (thresholds/timing not published) — [Mission Control](https://www.coreweave.com/mission-control); [3 ways Mission Control simplifies cluster management](https://www.coreweave.com/blog/3-ways-coreweave-mission-control-simplifies-cluster-management)
- **Node configuration visibility (Feb 4, 2026):** Node Pool status gains `nodeConfigurationRevisions` (every applied configuration, node counts, disabled-after-boot-failure flag) and `pendingNodeConfiguration` (staged changes awaiting approval); `nodeConfigurationUpdateStrategy` = Manual / OnSpecUpdate (default) / Always; manual rollback requires Manual — [Release note: Node configuration visibility](https://docs.coreweave.com/release-notes/nodeprofile-ux-updates); [Node Pool reference](https://docs.coreweave.com/products/cks/reference/node-pool)
- **Node Pool events tab (May 15, 2025)** and Node Pool condition transition (June 30, 2025) — [Node Pool UI enhancements](https://docs.coreweave.com/changelog/release-notes/node-pool-ui); [Node Pool conditions](https://docs.coreweave.com/docs/changelog/release-notes/2025-06-30-node-pool-conditions); [Node Pool status](https://docs.coreweave.com/products/cks/nodes/nodepool-status)
- **W&B integration (announced June 18, 2025; Preview):** Mission Control infrastructure alerts (GPU failures, thermal violations, auto-remediation status) shown on W&B run plots; only for CKS clusters (incl. SUNK on CKS); needs wandb ≥0.20.1, server ≥0.73.0 for dedicated/self-managed — [W&B infrastructure alerts](https://docs.wandb.ai/models/runs/infrastructure-alerts); [Press release](https://www.coreweave.com/news/coreweave-and-weights-biases-announce-new-products-and-capabilities-helping-ai-developers-iterate-faster-on-models-and-agents)
- **Vendor goodput claims:** up to 96% training goodput, 97–98% effective training time, 10× longer MTTF for thousand-GPU clusters (CoreWeave figures, GTC Mar 4, 2026) — [SUNK expands capabilities (2026)](https://investors.coreweave.com/news/news-details/2026/CoreWeave-SUNK-Expands-Capabilities-to-Bring-AI-Workloads-Online-Faster--Anywhere/default.aspx); [Goodput blog](https://www.coreweave.com/blog/achieve-ai-infrastructure-goodput-of-up-to-96-with-3-key-strategies)
- **ClusterMAX:** 2.0 (Nov 6, 2025) Platinum — sole Platinum, credited for active/passive health checks and automated node replacement (per CoreWeave PR); 3.0 (Sept 23, 2026) Platinum again, now alongside Nebius (moved up from Gold); 77 providers reviewed — [CoreWeave PR (2.0)](https://www.businesswire.com/news/home/20251105957267/en/CoreWeave-Achieves-SemiAnalysis%E2%80%99-Platinum-ClusterMAX%E2%84%A2-Rating-for-the-Second-Consecutive-Ranking-Remaining-the-Industrys-Sole-Platinum-Provider); [CoreWeave 3× Platinum blog](https://www.coreweave.com/blog/coreweave-becomes-the-only-provider-to-earn-three-consecutive-platinum-clustermax-tm-ratings); [ClusterMAX 3.0](https://newsletter.semianalysis.com/p/clustermax-30-the-industry-standard); [Blockspace](https://blockspace.media/insight/clustermax-3-gpu-cloud-rankings-coreweave-nebius/); [Nebius blog](https://nebius.com/blog/posts/nebius-platinum-clustermax-3-0)
- **SemiAnalysis nuance:** CoreWeave's active checks are "unusually robust" but it is "certainly not the only provider with active health checks" — [ClusterMAX 3.0 (via search extract)](https://newsletter.semianalysis.com/p/clustermax-30-the-industry-standard)

### Inferences
- Health-check outcomes are expressed as Kubernetes-native signals (taints, labels, conditions, events), so customer schedulers/operators can react without a proprietary API; but automation can override manual cordons.
- Prefill vs autoscaling are mutually exclusive, so customers choose between proactive replacement and elastic scaling per pool.

### Gaps
- Exact passive-check thresholds and time-to-replacement SLOs are not published.
- Whether NCCL tests are part of recurring HPC Verification (vs only bring-up) is unconfirmed.
- Full ClusterMAX 3.0 CoreWeave review text is paywalled/unreachable; criticisms beyond those listed not verified.

## 5. Autoscaling

### Takeaway
CKS uses the upstream Kubernetes Cluster Autoscaler per Node Pool (min/max nodes; Kubernetes ≥1.32), with notable exclusions:
rack-based GB200/GB300 pools and SUNK pools cannot autoscale; prefill is incompatible with autoscaling. Explicit scale-to-zero
for autoscaled pools was not confirmed.

### Cited Findings
- CKS uses the Kubernetes Cluster Autoscaler to scale Node Pools on GPU/CPU/memory demand; a pool scales as a unit between `minNodes` and `maxNodes`; requires Kubernetes 1.32+ — [Autoscale Node Pools](https://docs.coreweave.com/products/cks/nodes/autoscaling)
- Scale-down uses the autoscaler's own utilization logic, not CWActive, so an underutilized but active node can be removed; the autoscaler ignores the pool's scaling strategy — protect nodes with the annotation — [Autoscale Node Pools](https://docs.coreweave.com/products/cks/nodes/autoscaling)
- SUNK pools cannot autoscale; rack-based types (GB200, GB300) reject `autoscaling: true` — [Autoscale Node Pools](https://docs.coreweave.com/products/cks/nodes/autoscaling)
- Manifests with `minNodes: 0` exist (example with `autoscaling: false`) — [Create a Node Pool](https://docs.coreweave.com/products/cks/nodes/create)
- Spot pools persist at zero nodes after full preemption and scale back up when capacity returns — [Spot Node Pools](https://docs.coreweave.com/platform/capacity-plans/spot-node-pools)
- A "NodePools and Cluster Autoscaler" Grafana dashboard was added — [Changelog](https://docs.coreweave.com/changelog)
- SUNK known limitation (docs updated Sept 2026): Kubernetes chooses pods to remove on scale-down without coordinating with Slurm, leaving partially used GPU nodes — [SUNK Pod Scheduler](https://docs.coreweave.com/products/sunk/run_workloads/manage-scheduler-resources)

### Inferences
- Autoscaling is effectively for single-node GPU types (HGX H100/H200/B200/B300, RTX PRO) and CPU pools; rack-scale NVL72 capacity is managed statically (reservations), which matches how NVL72 is sold.
- Scale-up is bounded by quota and actual capacity in the zone (quota fill-up behavior), so autoscaling does not guarantee capacity outside reservations.

### Gaps
- No explicit confirmation of autoscaled scale-to-zero (`minNodes: 0` with `autoscaling: true`).
- No data on bare-metal node provisioning latency during scale-up.

## 6. Storage & networking

### Takeaway
Storage: CoreWeave AI Object Storage (S3-compatible, no egress/ingress/request fees, automatic Hot/Warm/Cold tiering) accelerated
in-cluster by LOTA (per-node 1 TiB cache, up to ~7 GB/s per GPU claimed), Distributed File Storage (VAST-backed POSIX RWX via
`shared-vast` storage class; 10 TiB default per cluster), local NVMe, and Dedicated VAST. Networking: Cilium (v1.18.1) CNI with
network policies, VPCs with Pod/Service/internal-LB CIDRs, public/private LoadBalancers via Cilium LB IPAM, NAT egress with
published per-AZ IP ranges, NodeLocal DNSCache, Tailscale/Direct Connect private access.

### Cited Findings
- **CAIOS tiers/fees:** automatic Hot (<7 days), Warm (7–30 days), Cold (30+ days) tiers; no egress, ingress or request fees; no tiering/retrieval/early-deletion fees; claimed >75% cost cut for typical AI workloads — [Storage for AI](https://www.coreweave.com/products/storage); [Expanding AI Object Storage blog](https://www.coreweave.com/blog/expanding-coreweave-ai-object-storage-unified-dataset-lower-costs-and-unmatched-scale)
- **CAIOS launch** (GA announced Mar 2025) — [CoreWeave launch blog](https://www.coreweave.com/blog/coreweave-launches-ai-object-storage-a-cutting-edge-object-storage-service-optimized-for-ai-workloads); [StorageNewsletter](https://www.storagenewsletter.com/2025/03/27/coreweave-launches-ai-object-storage/)
- **Cross-region writes and Archive tier** added — [Cross-region writes, Archive blog](https://wf.coreweave.com/blog/new-in-coreweave-ai-object-storage-cross-region-writes-and-archive-storage); cross-region LOTA acceleration GA from Oct 16, 2025, multi-cloud/hybrid planned for early 2026 (status unconfirmed) — [Blocks & Files (Oct 16, 2025)](https://blocksandfiles.com/2025/10/16/coreweaves-lota-tech-moves-object-data-at-high-speed/)
- **LOTA:** intelligent proxy on every GPU and CPU Node in a CKS cluster; in-cluster endpoint `http://cwlota.com`, external `https://cwobject.com`; each Node contributes 1 TiB cache by default; LRU eviction; cache pre-staging supported — [LOTA](https://docs.coreweave.com/products/storage/object-storage/improving-performance/about-lota); [Pre-stage the LOTA cache](https://docs.coreweave.com/products/storage/object-storage/improving-performance/prestage-cache)
- **LOTA throughput:** "up to 7 GB/s per GPU" (vendor peak; Forbes says "reportedly") — [Forbes (Oct 22, 2025)](https://www.forbes.com/sites/janakirammsv/2025/10/22/coreweave-empowers-ai-with-high-speed-flexible-object-storage/); [Storage for AI](https://www.coreweave.com/products/storage)
- **CAIOS audit logging** available — [Audit logging for AI Object Storage](https://docs.coreweave.com/products/storage/object-storage/auth-access/audit-logging); CAIOS SLA ≥99.9% — [AI Object Storage SLA](https://docs.coreweave.com/policies/terms-of-service/coreweave-ai-object-storage-policy)
- **Zero Egress Migration (0EM):** covers egress fees from third-party clouds for initial migration — [0EM announcement](https://www.coreweave.com/news/coreweave-announces-zero-egress-migration-unlocking-multi-cloud-development-for-ai-workloads)
- **Distributed File Storage:** POSIX shared filesystem; PVC with `ReadWriteMany` and `storageClassName: shared-vast`; avoid subPath mounts; PVCs can be rebound across namespaces via utility script — [Create DFS volumes](https://docs.coreweave.com/products/storage/distributed-file-storage/create-volumes); [Manage DFS volumes](https://docs.coreweave.com/products/storage/distributed-file-storage/manage-volumes); [About CoreWeave Storage](https://docs.coreweave.com/docs/products/storage)
- **Default quotas (recently reduced):** CAIOS 20 TiB per AZ; DFS 10 TiB per CKS cluster — [Changelog](https://docs.coreweave.com/changelog)
- **CSI:** CKS clusters come with a pre-installed CSI driver — [CKS introduction](https://docs.coreweave.com/products/cks); Dedicated VAST Storage uses standard VAST CSI drivers — [Dedicated VAST Storage](https://www.coreweave.com/products/storage/dedicated-vast-storage); local NVMe storage documented — [About local storage](https://docs.coreweave.com/products/storage/local-storage)
- **Zot registry on CKS (Feb 23, 2026):** tutorial for an OCI registry backed by CAIOS and read via LOTA — [Release note: Zot](https://docs.coreweave.com/release-notes/cks-zot-registry)
- **CNI:** Cilium v1.18.1 default for new clusters (namespace `cw-cilium-system`); Calico deprecated / not supported for new clusters — [Cluster components](https://docs.coreweave.com/products/cks/reference/cluster-components); [CNI concepts](https://docs.coreweave.com/docs/products/networking/concepts/container-network-interface)
- **Network policies:** Cilium/Kubernetes network policies supported; default-deny egress must allow CoreWeave platform services CIDR `100.124.0.0/18` (else API/DNS break); DNS egress should target node-local-dns; FQDN-based egress needs the L7 DNS proxy, which is a non-default setting customers cannot change because CoreWeave manages Cilium config — [Cilium network policy on CKS](https://docs.coreweave.com/products/networking/cilium-network-policy-cks-patterns); [Deploy network policies](https://docs.coreweave.com/security/tutorials/deploying-network-policies)
- **Tetragon** eBPF security observability tutorial available — [eBPF security observability with Cilium Tetragon](https://docs.coreweave.com/security/tutorials/ebpf-observability)
- **VPC:** a VPC needs at least 3 prefixes for CKS: Pod CIDR, Service CIDR, internal LoadBalancer CIDR; custom VPCs supported — [VPCs](https://docs.coreweave.com/docs/products/networking/concepts/vpc)
- **Load balancers:** LoadBalancer Services get public IPs (from the region's public LB pool, separate from NAT egress range) or private IPs (reachable in-cluster and over connected VPC / Direct Connect); address-pool pinning by label; Cilium LB IPAM replaces MetalLB on new clusters — [Expose a Service](https://docs.coreweave.com/products/networking/ingress-service/expose-service-dns); [Cluster components](https://docs.coreweave.com/products/cks/reference/cluster-components)
- **Egress:** pods egress via NAT with documented public IP ranges per AZ for external allowlisting — [About networking](https://docs.coreweave.com/products/networking)
- **DNS:** CKS serves in-cluster DNS from node-local-dns DaemonSet (NodeLocal DNSCache), not the standard kube-dns Service — [Cluster components](https://docs.coreweave.com/products/cks/reference/cluster-components)

### Inferences
- CoreWeave positions object storage + LOTA (not a parallel filesystem) as the primary high-throughput data path for training/inference, with DFS for POSIX/shared needs; zero egress fees are a strong differentiator for multi-cloud data movement.
- Managed Cilium configuration constrains advanced use (e.g., FQDN egress policies), a trade-off of the fully managed CNI.

### Gaps
- Per-GB CAIOS tier prices and DFS throughput/IOPS figures not found.
- Network Operator / GPUDirect RDMA / GPUDirect Storage configuration on CKS not documented in results.
- Egress pricing for compute (non-storage) traffic not verified.

## 7. Ecosystem: SUNK, Tensorizer, W&B, observability, CoreWeave Charts, Kueue/Ray/Kubeflow

### Takeaway
CKS is the base for SUNK (Slurm on Kubernetes, incl. a fully managed "SUNK Self-Service" mode), CoreWeave Charts (validated Helm
catalog incl. `cks-kueue`), documented KubeRay+Kueue and Kubeflow guides, free managed Grafana (CoreWeave Observe) with
PromQL/LogQL APIs and self-serve Telemetry Relay export, W&B (acquired May 5, 2025) with Mission Control alert integration, and the
open-source Tensorizer model loader.

### Cited Findings
- **SUNK Self-Service:** user defines a Slurm cluster as one Kubernetes resource; CoreWeave handles compute nodes, Slurm control plane, login pods, storage and access on CKS; recommended entry point — [About SUNK Self-Service](https://docs.coreweave.com/products/sunk/about-self-service-sunk)
- **SUNK features:** unified Slurm and Kubernetes workflows on one cluster with Mission Control observability/automated recovery (GTC, Mar 4, 2026); topology-aware scheduling; node locking (idle Slurm nodes can run K8s pods, evicted when a Slurm job starts) — [SUNK expands capabilities (2026)](https://investors.coreweave.com/news/news-details/2026/CoreWeave-SUNK-Expands-Capabilities-to-Bring-AI-Workloads-Online-Faster--Anywhere/default.aspx); [Node locking](https://docs.coreweave.com/products/sunk/discover_sunk/node-locking)
- **SUNK pitfalls:** Guaranteed-QoS pods with integer CPU requests get pinned cores, causing failures/drains; fix by Burstable QoS — [Static CPU allocation](https://docs.coreweave.com/products/sunk/run_workloads/static-cpu-allocation); open-source Slinky slurmrestd memory leaks around ~100k queued jobs (ClusterMAX 2.0, upstream flaw) — [ClusterMAX 2.0](https://newsletter.semianalysis.com/p/clustermax-20-the-industry-standard)
- **SUNK pricing:** "Free*" excluding CPU resources needed to run it — [CoreWeave pricing](https://www.coreweave.com/pricing)
- **CoreWeave Charts:** CoreWeave-hosted Helm collection tested/validated for CKS, incl. CoreWeave-built and third-party charts — [Introduction to CoreWeave Charts](https://docs.coreweave.com/products/cks/clusters/coreweave-charts/introduction)
- **Ray:** install KubeRay operator, then Kueue for queuing Ray clusters — [Run Ray with Kueue](https://docs.coreweave.com/products/cks/clusters/frameworks/ray-with-kueue)
- **Kubeflow:** a "Run Kubeflow" guide exists in CKS frameworks docs — [Run Kubeflow](https://docs.coreweave.com/products/cks/clusters/frameworks/kubeflow)
- **Inference on CKS (Mar 31, 2026):** "CoreWeave Inference" release; CKS gives full control over the inference stack via Kubernetes resources; vLLM inference tutorial with monitoring setup — [Release note: CoreWeave Inference](https://docs.coreweave.com/release-notes/inference); [Deploy vLLM: set up monitoring](https://docs.coreweave.com/docs/products/cks/tutorials/deploy-vllm-inference/set-up-monitoring)
- **Managed Grafana (CoreWeave Observe):** free, no setup, preconfigured read-only dashboards for metrics, events and logs; custom dashboards in preview; no custom alerting; access via Cloud Console with Observability Viewer role; Prometheus/Loki-compatible HTTP APIs (PromQL/LogQL); Super Regional log sources in US East, US West, EU South; self-hosted Grafana option in-cluster — [CoreWeave Grafana](https://docs.coreweave.com/observability/managed-grafana); [Query logs and metrics](https://docs.coreweave.com/observability/logs-metrics/introduction); [Self-hosted Grafana](https://docs.coreweave.com/observability/self-hosted-grafana); [CoreWeave Observe](https://docs.coreweave.com/observability)
- **Fleet dashboards:** Node Details fleet dashboard — [Node Details](https://docs.coreweave.com/observability/managed-grafana/fleet/node-details); cost & usage monitoring by storage class etc. — [Cost and usage monitoring](https://docs.coreweave.com/observability/usage-monitoring)
- **Telemetry Relay:** forwards telemetry (incl. Kubernetes/Teleport audit logs) to external endpoints; now self-configurable via Console/API without Support — [Changelog](https://docs.coreweave.com/changelog)
- **W&B:** acquired May 5, 2025; now part of "CoreWeave Forge"; Mission Control events appear in W&B workspace tied to runs (timelines, GPU errors, remediation guidance) — [W&B is part of CoreWeave](https://www.coreweave.com/companies/weights-and-biases); [W&B Models product page](https://www.coreweave.com/products/coreweave-forge/weights-and-biases-models); [W&B infrastructure alerts](https://docs.wandb.ai/models/runs/infrastructure-alerts)
- **Tensorizer:** open-source (github.com/coreweave/tensorizer) single-file model/tensor serialization streaming from HTTP(S)/S3 for fast loads (motivated by Knative scale-up latency); integrated in vLLM (`vllm[tensorizer]`) — [CoreWeave Tensorizer blog](https://coreweave.com/blog/coreweaves-tensorizer-decrease-pytorch-model-load-times); [vLLM Tensorizer docs](https://docs.vllm.ai/en/v0.8.4/models/extensions/tensorizer.html); [PyPI](https://pypi.org/project/tensorizer/2.8.1)
- **NCCL tests repo:** coreweave/nccl-tests images bundle DCGM diagnostics and RDMA tests — [coreweave/nccl-tests](https://github.com/coreweave/nccl-tests)

### Inferences
- The ecosystem is vertically integrated (infra → Mission Control → W&B), giving ML teams run-level infra-failure attribution that generic managed K8s does not provide natively.
- Managed Grafana lacking custom alerting pushes customers needing alerts to Telemetry Relay export or self-hosted Grafana.

### Gaps
- No evidence of a self-serve "Marketplace" UI (older CoreWeave Classic "Applications Catalog" not verified for CKS); CoreWeave Charts appears to be the current Helm channel.
- Tensorizer's current maintenance status/version for 2026 not verified.

## 8. Regions/zones, quotas, pricing model

### Takeaway
CoreWeave organizes capacity into Super Regions (US-EAST, US-CENTRAL, US-WEST, EU-NORTH/SOUTH/WEST), Regions and AZs, split into
General Access and Dedicated Access; quotas are per org × instance type × compute class × zone. Pricing: free control plane,
per-instance-hour compute under Reservation/Flex/On-Demand/Spot, zero-egress object storage.

### Cited Findings
- Regions are General Access (all customers, subject to capacity) or Dedicated Access (select customers); AZs grouped by Super Region; EU zones in Sweden, Norway, Denmark (EU-NORTH), Spain (EU-SOUTH, e.g., EU-SOUTH-03B, EU-SOUTH-04A General Access), UK (EU-WEST) — [All Availability Zones](https://docs.coreweave.com/platform/regions/all-availability-zones); [Regions and AZs](https://docs.coreweave.com/docs/platform/regions)
- Dedicated Access Region US-EAST-11 (North Carolina) added — [Changelog](https://docs.coreweave.com/release-notes/changelog)
- Availability varies by instance/zone — [Instance availability matrix](https://docs.coreweave.com/platform/instances/availability-matrix)
- Quotas and capacity model — see section 2 — [Resource quotas](https://docs.coreweave.com/products/cks/clusters/quotas)
- Third-party note: CoreWeave changed rate-card shape July 21, 2026 without moving published GPU prices — [search extract of third-party pricing site](https://www.spheron.network/blog/coreweave-gpu-pricing-2026/) (unverified)

### Inferences
- Region choice is constrained by Dedicated vs General Access; many large deployments sit in dedicated regions not available to self-serve customers.

### Gaps
- Complete current US zone list not captured.
- No published per-region pricing differences beyond the EU vs NA spot example.

## 9. Notable 2025–2026 announcements, limitations, customer reports

### Takeaway
2025–2026 saw CKS mature toward self-service (API/Terraform, IAM policies, OIDC WIF, Spot pools, Flex capacity, Telemetry Relay
self-serve, node configuration revisions) and rack-scale hardware (GB300 Aug 2025, Vera Rubin LA Sept 2026). Limitations include
private clusters only via Support, managed-Cilium constraints, no autoscaling for NVL72/SUNK pools, no custom alerting in managed
Grafana, and a handful of 2026 incidents affecting CKS API/console.

### Cited Findings
- **Timeline:** CKS API + Terraform (Feb 21, 2025); Node Pool UI events (May 15, 2025); v1.32 (Jun 13, 2025); Node Pool conditions (Jun 30, 2025); Control-plane node pools removed + unmanaged auth API (Jul 7, 2025); GB300 NVL72 (Aug 19, 2025); ClusterMAX 2.0 Platinum (Nov 6, 2025); OIDC WIF + IAM Access Policies (Nov 20, 2025); node configuration visibility (Feb 4, 2026); Zot registry tutorial (Feb 23, 2026); SUNK expansion at GTC (Mar 4, 2026); Spot Node Pools + capacity plans (Mar 10, 2026); Support Access Management (Mar 26, 2026); CoreWeave Inference (Mar 31, 2026); Vera Rubin bring-up (Jun 1, 2026), multi-rack (Sept 16, 2026), LA (Sept 30, 2026); ClusterMAX 3.0 Platinum (Sept 23, 2026) — sources cited in sections above; [Changelog](https://docs.coreweave.com/changelog)
- **CoreWeave ARENA** lab for AI production readiness announced — [ARENA announcement](https://www.coreweave.com/news/introducing-coreweave-arena-new-lab-for-ai-production-readiness)
- **2026 incidents (third-party status mirror):** Aug 13, 2026 EU-SOUTH-03B network issue made Kubernetes API calls fail/lag (~1 hr; workloads unaffected); Aug 26, 2026 console/Terraform CKS create/update/delete failures (22 min); Aug 5, 2026 US-EAST-04A networking hardware made some nodes unschedulable; Oct 5, 2026 US-CENTRAL-09A networking incident (~2 hr, "Major") — [IsDown CoreWeave incidents](https://isdown.app/status/coreweave/incidents/665120-us-central-09a-networking-incident); [Cloud console disruption](https://isdown.app/status/coreweave/incidents/644505-cloud-console-service-disruption); [US-EAST-04A](https://isdown.app/status/coreweave/incidents/633820-network-connectivity-issue-affecting-a-subset-of-nodes-in-us-east-04a)
- **Limitations (documented):** private clusters require Support; FQDN egress policies unavailable (managed Cilium); GB200/GB300 and SUNK pools cannot autoscale; prefill incompatible with autoscaling/Spot; managed Grafana dashboards read-only and no custom alerting; GPU Operator cannot be self-installed; manual cordons may be cleared by automation — sources in sections 1, 3–7
- **Limitations (third-party):** host-to-BMC access disabled (too limiting for some power users, ClusterMAX 2.0); historically few short-term rentals — [ClusterMAX 2.0](https://newsletter.semianalysis.com/p/clustermax-20-the-industry-standard); [ClusterMAX original](https://newsletter.semianalysis.com/p/the-gpu-cloud-clustermax-rating-system-how-to-rent-gpus)

### Inferences
- Competitive gap vs Nebius narrowed in 2026: both now Platinum in ClusterMAX 3.0, so CKS differentiation rests on scale/early hardware access (GB300, Vera Rubin), Mission Control lifecycle automation, CAIOS/LOTA and W&B integration.

### Gaps
- No independent, detailed customer reviews of CKS day-to-day UX found (beyond SemiAnalysis and incident mirrors).
- Full ClusterMAX 3.0 per-provider text unreachable; Nebius vs CoreWeave sub-scores not captured.
