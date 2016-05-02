# Stable Plugins DSL

**Owners:**
Pepper Lebeck-Jobe [@eljobe](https://github.com/eljobe)
Stefan Oehme [@oehme](https://github.com/oehme)   
**Updated:** 2016-05-02   
![Approved](https://img.shields.io/badge/design-approved-green.svg)

# Objective

Enhance the functionality of the `plugins` element of the Gradle Domain Specific language sufficiently to
allow light-weight inclusion of plugins from and publishing of plugins to Maven and Ivy artifact repositories.
It should also be convenient to apply plugins to multiple projects in a multiproject build within a single construct
similar to how `allprojects {}` and `subprojects {}` works.

# Solution

The solution we are pursuing is broken into two high-level milestones, with the first having three discrete objectives,
each of which are described in more detail in a separate design doc. This document serves as a high-level map of how those
pieces come together to accomplish the objective.

* **M1** Resolve plugins from a maven/ivy repository based on the plugin id and version

    * **[M1.1](M1.1.md)**   
      ![Approved](https://img.shields.io/badge/design-approved-green.svg)   
      Be able to map from plugin id and version to a maven/ivy artifact

    * **[M1.2](https://docs.google.com/document/d/139-eP7JhUvuVKfHUEk4fNFR_vzOGKsB4WYYrHLPe_6s)**   
      ![For Review](https://img.shields.io/badge/design-for_review-yellow.svg)   
      Specify which maven/ivy repositories to search for plugins

    * **[M1.3](https://docs.google.com/document/d/1n9CTekaRt1tybw1qiXPtQD5gwI6CQLHzQSliDX7_vc0/edit)**   
      ![For Review](https://img.shields.io/badge/design-for_review-yellow.svg)   
      Publish plugins to a maven/ivy repository complete with metadata needed to map from the plugin id and version to a maven/ivy artifact and its dependencies

* **[M2](https://docs.google.com/document/d/1uy8mqv_ZuvLUh10P43VPkaPEmofckvOnRjtaA9_quzI/edit)**   
  ![For Review](https://img.shields.io/badge/design-for_review-yellow.svg)   
  Specify plugins to be used on multiple projects in a multi-project build in a single code block
