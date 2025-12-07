Good morning/afternoon, everyone. Thank you for taking the time to join me today as we dive into a critical initiative on migration from HTTP to HTTPS for the WebNewServices Gateway Intranet.

Let’s start with the “why” behind this project. For those who aren’t familiar, the WebNewServices Gateway is the backbone of our daily operations: it’s an enterprise-grade reverse proxy that serves over 1,500 active application backends and processes ~10 million requests every single day. It’s our central hub for traffic management, handling everything from authentication to intelligent routing.

But here’s the challenge: up until now, we’ve been running on http://intranet.WebNewServices.com and that comes with real, tangible risks. Imagine sending your password, API keys, or sensitive client data over a connection that’s like talking in a crowded room: anyone could be listening. That’s plain text transmission, and it opens us up to man-in-the-middle attacks. Beyond security, we’re facing compliance gaps, data integrity issues, and even “Not Secure” warnings in browsers probabily.

And it’s not just security—modern browsers are leaving HTTP behind. Features like push notifications, geolocation, and even basic cookie attributes (like Secure or SameSite) are blocked or restricted over HTTP. We’re missing out on the speed and efficiency of HTTP/2 and HTTP/3. In short, HTTP is holding us back from using the tools and technologies that keep our applications modern and user-friendly.

Now, let’s shift to the “what’s in it for us.” By moving to https://apps.WebNewServices.com, we’re not just checking a box—we’re transforming how we protect and power our applications. End-to-end encryption means sensitive data stays private. Certificate-based authentication stops man-in-the-middle attacks in their tracks. We’ll meet industry compliance standards, guarantee data integrity, and eliminate those browser warnings to rebuild user confidence.

On the modernization side, we’ll unlock support for the latest web APIs and frameworks, fully leverage cookie attributes to keep sessions secure, and future-proof our infrastructure against evolving browser requirements. And down the line, HTTP/2 and HTTP/3 support will make our pages load faster—something every user will notice.

Of course, no project this size comes without hurdles. Let’s talk about the risks we’ve identified and how we’re planning to mitigate them. For example, some applications have hard-coded HTTP URLs instead of relative paths—fixing that takes careful work. We also have legacy vendor tools that don’t support HTTPS, network restrictions that could block the new domain, and a small set of unsupported applications with no available resources.

To address these, we’ve built a migration strategy that prioritizes minimal business impact. Firstly, we’re using a new secure domain (apps.WebNewServices.com) for the HTTPS migration, with additional configuration via CPA to enforce redirects. We’ll run dual HTTP/HTTPS support during the transition period, and we’ve already completed regression testing to ensure both protocols work the same way.

For mitigation, we’re doing pre-production testing in dedicated INT and UAT environments, rolling out the change in phases (so we can roll back if needed), and monitoring adoption rates with comprehensive auditing reports. We’re also working with key partners to test before cutover and collecting firewall change histories in ServiceNow to track every detail.

And to give you a sense of scale: 84% of our traffic is from web browser applications, which require no code changes—just updating some hard-coded URLs to relative paths. 11% is programmatic visits (needing URL updates and certificate trust config), and 5% is IE-mode applications (handled mostly by PAC file config, with some library upgrades if needed).

Finally, let’s look at our roadmap and expected outcomes. Our goal is a zero-downtime transition across all 1,500+ backends, with a seamless user experience and a phased rollout that keeps contingency plans ready. We’ll end up with an enhanced security posture and infrastructure that’s aligned with modern web standards.

Operationally, this migration will align our CPA configurations with CMDB, help us achieve F5 Load Balancer migration, and even reduce our DNS aliases from 16 to 7—making maintenance simpler.

Right now, we’ve completed PROD deployment, have SSL endpoints ready, and finished testing. Next, we’ll move through Foundation (completing SSL for critical admin apps), Pilot Rollout (updating PAC files and tracking programmatic cutover), Mass Migration (switching to HTTPS-only by default), Exception Handling (managing edge cases), and finally Decommissioning (removing HTTP load balancers and cleaning up DNS).

In closing, this project isn’t just about switching a protocol—it’s about keeping WebNewServices’s applications secure, compliant, and ready for the future. It’s a big lift, but with our careful strategy and mitigation plans, we’re confident we’ll deliver value with minimal disruption.
Would you like me to help you add speaker notes for each slide to make the presentation flow even smoother?
