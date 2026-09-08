# -*- coding: utf-8 -*-
import argparse
import json
import re
import sys
from pathlib import Path


LANGUAGE_CODES = [
    "zh-Hans",
    "zh-Hant-HK",
    "zh-Hant-TW",
    "en",
    "fr",
    "es",
    "ja",
    "ko",
    "ru",
    "ar",
]
LANGUAGE_CODE_DEFAULT = "zh-Hans"
README_COMPAT_ROOT_LANGUAGES = set()
ANDROID_CHANGELOG_ALIASES = {
    "zh-Hans": ["zh", "zh-Hans"],
    "zh-Hant-HK": ["zh-rHK", "zh-Hant-HK"],
    "zh-Hant-TW": ["zh-rTW", "zh-Hant-TW"],
}


def project_root() -> Path:
    return Path(__file__).resolve().parents[1]


ROOT = project_root()
README_DIR = ROOT / ".readme"
CHANGELOG_DIR = ROOT / ".changelog"
ANDROID_CHANGELOG_DIR = ROOT / "app" / "src" / "main" / "assets" / "doc"


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def render_template(text: str, values: dict) -> str:
    def repl(match):
        key = match.group(1).strip()
        if key not in values:
            raise KeyError(f"Missing template value: {key}")
        return str(values[key])

    return re.sub(r"\{\{\s*([A-Za-z0-9_$.-]+)\s*\}\}", repl, text)


def render_dynamic(value, values: dict):
    if isinstance(value, dict):
        return {k: render_dynamic(v, values) for k, v in value.items()}
    if isinstance(value, list):
        return [render_dynamic(v, values) for v in value]
    if isinstance(value, str):
        return render_template(value, values)
    return value


def bullet_list(items):
    return "\n".join(f"- {item}" for item in items)


def markdown_link(label, url):
    return f"[{label}]({url})"


def load_languages():
    common = load_json(README_DIR / "common.json")
    languages = {}
    changelogs = {}
    for code in LANGUAGE_CODES:
        raw_lang = load_json(README_DIR / f"lang_{code}.json")
        merged_lang = {**common, **raw_lang}
        languages[code] = render_dynamic(merged_lang, merged_lang)

        raw_changelog = load_json(CHANGELOG_DIR / f"lang_{code}.json")
        changelog_values = {k: v for k, v in raw_changelog.items() if k != "$data"}
        changelog_values = render_dynamic(changelog_values, changelog_values)
        changelog_data = render_dynamic(raw_changelog["$data"], changelog_values)
        changelogs[code] = {
            "values": changelog_values,
            "data": changelog_data,
        }
    return languages, changelogs


def format_changelog_items(changelog, limit=None):
    values = changelog["values"]
    data = changelog["data"]
    chunks = []
    for index, (version_name, item) in enumerate(data.items()):
        if limit is not None and index >= limit:
            break
        lines = [
            f"# {version_name}",
            "",
            f"###### {item['released_date']}",
            "",
        ]
        for category in ["hint", "feature", "fix", "improvement", "dependency"]:
            for text in item.get(category, []):
                label = values[f"changelog_label_{category}"]
                lines.append(f"* `{label}` {text}")
        chunks.append("\n".join(lines).rstrip())
    return "\n\n".join(chunks).rstrip() + "\n"


def build_language_list(target_code, languages):
    lines = []
    repo_url = languages[LANGUAGE_CODE_DEFAULT]["repo_url"]
    for code in LANGUAGE_CODES:
        content = languages[code]
        label = f"{content['$name']} [{code}]"
        if code == target_code:
            lines.append(f"- {label} # {content['text_current_lowercase']}")
        else:
            url = f"{repo_url}/blob/master/.readme/README-{code}.md"
            lines.append(f"- {markdown_link(label, url)}")
    return "\n".join(lines)


def build_readme_values(code, languages, changelogs):
    content = dict(languages[code])
    content["placeholder_ul_languages_all_supported"] = build_language_list(code, languages)
    content["placeholder_features"] = bullet_list(content["features"])
    content["placeholder_runtime_capabilities"] = bullet_list(content["runtime_capabilities"])
    content["placeholder_latest_release_history"] = format_changelog_items(changelogs[code], limit=3).rstrip()
    content["placeholder_read_more_in_changelog_md"] = markdown_link(
        "CHANGELOG.md",
        f"{content['repo_url']}/blob/master/.changelog/CHANGELOG-{code}.md",
    )
    return content


def write_text(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")
    print(f"Generated {path.relative_to(ROOT)}")


def generate_readmes(languages, changelogs):
    outputs = {}
    template = (README_DIR / "template_readme.md").read_text(encoding="utf-8")
    for code in LANGUAGE_CODES:
        output = render_template(template, build_readme_values(code, languages, changelogs))
        path = README_DIR / f"README-{code}.md"
        outputs[path] = output
        if code == LANGUAGE_CODE_DEFAULT:
            outputs[ROOT / "README.md"] = output
        if code in README_COMPAT_ROOT_LANGUAGES:
            outputs[ROOT / f"README-{code}.md"] = output
    return outputs


def generate_changelogs(languages, changelogs):
    outputs = {}
    template = (CHANGELOG_DIR / "template_changelog.md").read_text(encoding="utf-8")
    for code in LANGUAGE_CODES:
        values = dict(languages[code])
        values["placeholder_release_history"] = format_changelog_items(changelogs[code]).rstrip()
        output = render_template(template, values)
        outputs[CHANGELOG_DIR / f"CHANGELOG-{code}.md"] = output

        latest_only = format_changelog_items(changelogs[code], limit=1)
        names = ANDROID_CHANGELOG_ALIASES.get(code, [code])
        for name in names:
            outputs[ANDROID_CHANGELOG_DIR / f"CHANGELOG-{name}.md"] = latest_only
        if code == LANGUAGE_CODE_DEFAULT:
            outputs[ANDROID_CHANGELOG_DIR / "CHANGELOG.md"] = latest_only
    return outputs


def main():
    parser = argparse.ArgumentParser(description="Generate localized README and CHANGELOG files.")
    parser.add_argument("--check", action="store_true", help="Report missing or stale output without writing files.")
    args = parser.parse_args()
    if LANGUAGE_CODE_DEFAULT not in LANGUAGE_CODES:
        raise ValueError(f"Default language code {LANGUAGE_CODE_DEFAULT!r} is not in LANGUAGE_CODES")
    languages, changelogs = load_languages()
    outputs = {**generate_changelogs(languages, changelogs), **generate_readmes(languages, changelogs)}
    drift = []
    for path, output in outputs.items():
        if args.check:
            # Universal newlines allow Git's Windows CRLF checkout without masking content drift.
            if not path.is_file() or path.read_text(encoding="utf-8") != output:
                drift.append(path.relative_to(ROOT))
        else:
            write_text(path, output)
    if drift:
        for path in drift:
            print(f"Out of date: {path}", file=sys.stderr)
        print("Run: py .python/generate_markdown.py", file=sys.stderr)
        return 1
    if args.check:
        print(f"Checked {len(outputs)} generated files: no drift.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
