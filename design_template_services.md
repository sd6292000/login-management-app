Template Rendering System Architecture Document
Version: 1.0
Date: 2026-03-12
Description: 轻量级可扩展的模板渲染与管理系统架构设计，覆盖核心组件、业务流程、技术实现与功能规范，完全对齐手绘架构逻辑
1. Architecture Overview
This system is a template-based static HTML rendering solution, designed to dynamically generate localized static pages with configurable templates, business variables and multilingual labels. It provides full-lifecycle template management, multi-level cache optimization, and standard API capabilities, connecting end-user access requests and administrator operation requirements through a gateway layer.
2. System Architecture Diagram
flowchart TD
    %% Role Definition
    A[End User] -->|Request| B[Gateway]
    G[Admin User] -->|Manage| E[Template Manager]

    %% Core Component Flow
    B -->|variable| C[Template Fetcher]
    C -->|html content| B

    C -->|tags/path| E
    E -->|Template| C

    %% Data & Dependency Layer
    E <-->|template read/write| F[(Database)]
    H[Ref Data] -->|labeling| E

    %% Style Mapping (Aligned with Hand-drawn Color)
    classDef user fill:#fff,stroke:#007acc,stroke-width:2px
    classDef blueModule fill:#e6f3ff,stroke:#007acc,stroke-width:2px
    classDef greenModule fill:#e6ffe6,stroke:#009933,stroke-width:2px
    classDef orangeModule fill:#fff2e6,stroke:#e67300,stroke-width:2px
    classDef db fill:#e6ffe6,stroke:#009933,stroke-width:2px

    class A,G user
    class B,C blueModule
    class E,F greenModule
    class H orangeModule
3. Core Component Responsibilities
3.1 Gateway
System entry layer, accepts all end-user page access requests
Responsible for request routing, parameter validation, and forwarding business variables, path, language and other parameters to Template Fetcher
Receives fully rendered static HTML from Template Fetcher and returns it to the end user
3.2 Template Fetcher
Core rendering execution layer, the bridge between Gateway and Template Manager
Core duties: receive parameters from Gateway, match the optimal template from Template Manager, complete variable replacement and multilingual label injection, and render the final static HTML
Built-in multi-level cache capability, caches hot templates and rendering results to reduce latency and dependency calls for repeated requests
Implements template fallback logic, returns the default template when no matching template is found to ensure service availability
3.3 Template Manager
Core of template full-lifecycle management, provides template creation, editing, query, and version control capabilities
Receives matching requests from Template Fetcher, executes template matching logic based on path, tags, language and other parameters, and returns the optimal template
Connects to the Database to realize persistent storage of template metadata and HTML content
Connects to Ref Data service to obtain multilingual labels and support the internationalization capability of templates
Provides management UI and operation APIs for administrators to perform template O&M
3.4 Ref Data Service
System multilingual label management center, maintains full internationalized key-value pair data
Provides multilingual label query capability for Template Manager and Template Fetcher, supports batch acquisition of label content in specified languages
Implements multilingual fallback logic, automatically falls back to the default language when there is no corresponding label in the target language
Provides label management and maintenance capabilities to support the internationalization expansion of templates
3.5 Database
System persistent storage layer, stores full template data
Core storage content: template HTML content, path, tags, language, version number, status and other metadata
Supports template query, write, update and delete operations of Template Manager, and provides transaction and index optimization capabilities
4. Core Business Process
4.1 End-User Page Rendering Process
The end user initiates a page access request, and the request enters the Gateway
The Gateway completes request verification, extracts parameters such as path, business variables, and language preference, and calls Template Fetcher
Template Fetcher preferentially queries the matching template from the local cache. If the cache misses, it calls Template Manager with tags/path/language parameters
Template Manager executes multi-dimensional matching logic based on the incoming parameters, queries the optimal template from the Database, and returns it to Template Fetcher
Template Fetcher obtains multilingual labels in the corresponding language from Ref Data, and replaces the i18n placeholders in the template
Template Fetcher uses the business variables passed in by the Gateway to replace the business placeholders in the template, and generates the final static HTML
Template Fetcher returns the rendered HTML to the Gateway, and writes the template and rendering results into the cache
The Gateway returns the static HTML to the end user
4.2 Administrator Template Management Process
The administrator accesses Template Manager through the management UI and initiates a template creation/editing/deletion/preview request
Template Manager completes permission verification and executes the corresponding operations:
Create/Edit: Verify the uniqueness of path + language, update the template version, write template data into the Database, publish a template update event, and trigger cache invalidation
Delete: Perform soft delete, update the template status to inactive, and trigger cache invalidation
Preview: Call the rendering logic, generate preview HTML based on test variables and language, and return it to the management UI
After the operation is completed, Template Manager returns the result to the management UI and updates the template list synchronously
4.3 Multilingual Label Injection Process
Template Fetcher parses the {{i18n.*}} multilingual placeholders in the template and extracts all i18n keys
Template Fetcher calls the Ref Data service in batches with language parameters and key lists
Ref Data queries the label value of the corresponding language from the cache/database, falls back to the default language if there is no match, and returns the key-value pair
Template Fetcher replaces the multilingual placeholders in the template with the corresponding label values to complete internationalized rendering
5. Technology Stack (Java Implementation)
ModuleTechnology SelectionSelection DescriptionCore FrameworkSpring Boot 3.x + Spring Framework 6.xMature ecosystem, out-of-the-box, provides complete Web, cache, and data access capabilities, suitable for rapid Java developmentWeb API LayerSpring Web MVCStandardized RESTful API implementation, supports inter-service calls and front-end management UI dockingDatabase AccessSpring Data JPA + HibernateSimplifies database CRUD operations, supports dynamic query construction, and adapts to multi-dimensional template matching query requirementsCache LayerCaffeine (Local Cache) + Redis (Distributed Cache, Optional)- Caffeine: High-performance Java local cache, supports LRU/TTL elimination strategy, suitable for hot template caching- Redis: Global cache for distributed deployment, supports cache invalidation broadcastEvent NotificationSpring ApplicationEvent (Standalone) / RabbitMQ/Kafka (Distributed)Triggers cache invalidation events when templates are updated, ensuring cache data consistency in multi-instance deploymentManagement FrontendVue 3 + Element PlusLightweight and easy to expand, quickly build a visual interface for template management, supports HTML editor, preview, version management and other functionsDatabaseMySQL 8.x / PostgreSQL 15.xRelational database, adapts to the storage of template structured metadata, supports transactions, unique constraints and index optimizationMultilingual ManagementIndependent Ref Data MicroserviceDecoupled from the core template logic, independently maintains multilingual labels, and supports flexible expansion6. Core Feature Implementation Specification
6.1 Dependency-Free Template Rendering
No third-party template engine is introduced, using custom placeholder formats {{variableName}} and {{i18n.key}}
Placeholder parsing and replacement are implemented based on Java string regular replacement, with extremely simple logic, controllable performance, and no additional dependencies for template content modification
Core implementation example:
/**
 * Core method for template variable replacement
 * @param templateHtml Raw template HTML content
 * @param variables Business variable key-value pairs
 * @return Rendered HTML content
 */
public String renderTemplate(String templateHtml, Map<String, Object> variables) {
    String renderedHtml = templateHtml;
    // Replace business variable placeholders
    for (Map.Entry<String, Object> entry : variables.entrySet()) {
        String placeholder = "{{" + entry.getKey() + "}}";
        renderedHtml = renderedHtml.replace(placeholder, String.valueOf(entry.getValue()));
    }
    return renderedHtml;
}
6.2 Multi-Level Cache Design
Level 1 Cache (Local Cache): Implemented based on Caffeine, caches template metadata and raw HTML, the key is template:{path}:{lang}:{tagsHash}, default TTL 10 minutes, LRU elimination strategy, maximum capacity 1000 entries
Level 2 Cache (Distributed Cache, Optional): Implemented based on Redis, caches the final rendered static HTML, the key is the hash value of the full request parameters, suitable for ultra-fast response to repeated requests
Cache Invalidation Mechanism: When a template is updated/deleted, an event is published to trigger the invalidation of the corresponding cache key to avoid dirty data
6.3 Template Matching Logic
Adopt a weighted scoring mechanism, priority from high to low:
Matching ConditionWeight ScoreExact path match + Exact lang match10Single tag match5 per tagExact lang match3Fuzzy path match2Sort by score, return the active template with the highest score
When no matching template is found, automatically fall back to the global default template to ensure service availability
6.4 Multilingual Implementation Specification
Multilingual labels are uniformly stored in the Ref Data service, only {{i18n.key}} placeholders are reserved in the template, no hard-coded text
Support batch key query to reduce the number of inter-service calls
Fallback logic: No corresponding label in the target language → Fall back to the default language (en) → Still no match, retain the placeholder or display [Missing Translation]
Multilingual label cache: Ref Data service implements local cache for lang + key combinations to reduce database query pressure
7. Core User Story Overview
7.1 Template Fetcher User Stories
Basic Template Matching & Rendering: As a Gateway Service, I can call Template Fetcher with path, tags, lang, and business variables to get a fully rendered static HTML page
Multi-Criteria Template Selection: As a Template Fetcher, I can match the optimal template through multi-dimensional parameters such as path, tags, lang, and library ID
Local Cache Optimization: As a Template Fetcher, I can cache hot template data to reduce latency and dependency calls for repeated requests
Cache Invalidation Handling: As a Template Fetcher, I can listen to template update events and automatically invalidate the corresponding cache to avoid dirty data
Multilingual Support: As a Template Fetcher, I can connect to the Ref Data service to complete the replacement of template multilingual labels and support internationalized rendering
Fallback Guarantee: As a Template Fetcher, I can fall back to the default template when no matching template is found to ensure service availability
7.2 Template Manager User Stories
Template Creation & Management: As a Template Administrator, I can create, edit, and delete templates through a visual UI and persist them to the database
Template Metadata Management: As a Template Administrator, I can configure path, tags, lang and other metadata for templates to support template matching
Version Control: As a Template Administrator, I can view the historical version of the template and support rolling back to the specified version
Template Query API: As a Template Fetcher, I can call the matching API of Template Manager to get the optimal template
Template Preview: As a Template Administrator, I can preview the template rendering effect through test variables and languages, and release it after verification
Access Control: As a Template Manager, I can restrict template operation permissions, only authorized users can perform template editing and management
8. Appendix
8.1 Specification Glossary
Template Placeholder Specification: Business variables use {{variableName}}, multilingual labels use {{i18n.key}}
Language Code Specification: Adopt ISO 639-1 standard, such as zh-CN, en-US, ja-JP
Template Status Specification: active (available for matching and rendering), inactive (unavailable for matching)
8.2 Acceptance Criteria Core Principles
All API requests must have complete parameter verification and exception handling
Template rendering must complete within 100ms for cache hit requests, and within 500ms for cache miss requests
All template operations must have complete operation logs and version records
Multilingual rendering must support at least 2 languages and complete fallback logic