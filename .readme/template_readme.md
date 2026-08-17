<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="{{ repo_url }}/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>{{ text_plugin_synopsis }}</p>

  <p>
    <a href="{{ repo_url }}/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="{{ repo_url }}/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="{{ repo_url }}/commit/{{ created_commit }}"><img alt="Created" src="https://img.shields.io/date/{{ created_timestamp }}?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/Android%20Studio-2023.3+-B64FC8"/></a>
    <a href="https://www.jetbrains.com/idea/download/other.html"><img alt="IntelliJ IDEA" src="https://img.shields.io/badge/IntelliJ%20IDEA-2023.3+-EE4677"/></a>
    <a href="{{ repo_url }}/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### {{ h3_languages_with_ascii }}

******

{{ p_languages_all_supported_for_readme }}:

{{ placeholder_ul_languages_all_supported }}

******

### {{ h3_introduction }}

******

{{ p_introduction }}

******

### {{ h3_functions }}

******

{{ placeholder_features }}

******

### {{ h3_usage }}

******

```js
"nodejs";

console.log(process.version);
console.log("AutoJs6 Node.js runtime");
```

{{ p_usage_note }}

******

### {{ h3_quick_start }}

******

- **{{ quick_start_install_title }}** — {{ quick_start_install }}
- **{{ quick_start_run_title }}** — {{ quick_start_run }}
- **{{ quick_start_debug_title }}** — {{ quick_start_debug }}

******

### {{ h3_runtime_profile }}

******

{{ placeholder_runtime_capabilities }}

******

### {{ h3_release_history }}

******

{{ placeholder_latest_release_history }}

##### {{ h5_for_more_release_history }}

* {{ placeholder_read_more_in_changelog_md }}

******

### {{ h3_build }}

******

```powershell
.\gradlew.bat :app:assembleDebug
```

{{ text_release_build }}:

```powershell
.\gradlew.bat :app:assembleRelease
```

{{ p_build_params }}.

******

### {{ h3_resource_layout }}

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
app/src/main/assets/doc/CHANGELOG-*.md
```

{{ p_resource_layout }}.

******

### {{ h3_links }}

******

- {{ text_link_autojs6_docs }}: {{ docs_autojs6_url }}
- {{ text_link_nodejs_official }}: {{ nodejs_official_url }}
- {{ text_link_runtime_build_plan }}: {{ runtime_build_plan_path }}
