#!/usr/bin/env python3
"""Prepare repository-relative coverage and classpaths for the single Sonar project."""

from pathlib import Path
import xml.etree.ElementTree as ET

STACKS = ("async", "reactive")
SERVICES = ("gateway", "user", "tweet", "interaction")


def java_coverage(modules, output):
    coverage = ET.Element("coverage", version="1")
    for module in modules:
        report = module / "target/site/jacoco/jacoco.xml"
        file_count = 0
        for package in ET.parse(report).getroot().findall("package"):
            for source in package.findall("sourcefile"):
                path = module / "src/main/java" / package.attrib["name"] / source.attrib["name"]
                lines = source.findall("line")
                if not lines:
                    continue
                if not path.is_file():
                    raise ValueError(f"JaCoCo source path not found: {path}")
                file = ET.SubElement(coverage, "file", path=str(path))
                for line in lines:
                    n = line.attrib
                    attrs = {"lineNumber": n["nr"], "covered": str(int(n["ci"]) > 0).lower()}
                    branches = int(n["mb"]) + int(n["cb"])
                    if branches:
                        attrs.update(branchesToCover=str(branches), coveredBranches=n["cb"])
                    ET.SubElement(file, "lineToCover", attrs)
                file_count += 1
        if not file_count:
            raise ValueError(f"No Java coverage in {report}")
    ET.ElementTree(coverage).write(output, encoding="utf-8", xml_declaration=True)


def frontend_coverage(report, output, repo):
    sources = 0
    with output.open("w", encoding="utf-8") as writer:
        for line in report.read_text(encoding="utf-8").splitlines():
            if line.startswith("SF:"):
                path = (repo / "frontend" / line[3:]).resolve()
                if not path.is_relative_to(repo / "frontend/src") or not path.is_file():
                    raise ValueError(f"Invalid frontend source path: {path}")
                sources += 1
                line = f"SF:{path.relative_to(repo).as_posix()}"
            writer.write(line + "\n")
    if not sources:
        raise ValueError(f"No frontend coverage in {report}")


def main():
    repo = Path.cwd()
    modules = [Path("backend", stack, f"{service}-service") for stack in STACKS for service in SERVICES]
    output = Path("testing-results/sonar")
    output.mkdir(parents=True, exist_ok=True)
    java_coverage(modules, output / "java.xml")
    frontend_coverage(Path("frontend/coverage/lcov.info"), output / "frontend.lcov", repo)

    classes = [module / "target/classes" for module in modules]
    tests = [module / "target/test-classes" for module in modules]
    if not all(directory.is_dir() for directory in classes + tests):
        raise ValueError("Backend bytecode missing after Maven verify")

    # Every service is an independent Maven build. Match exact resolved jars rather
    # than scanning Maven plugin dependencies or assuming an aggregate reactor.
    libraries = set()
    for module in modules:
        files = (module / "target/sonar-classpath.txt").read_text(encoding="utf-8").strip().split(":")
        if not files or not all(Path(file).is_file() and file.endswith(".jar") for file in files):
            raise ValueError(f"Incomplete Maven classpath for {module}")
        libraries.update(files)

    props = {
        "sonar.host.url": "https://sonarcloud.io",
        "sonar.organization": "andreizb",
        "sonar.projectKey": "andreizb_tweebyte",
        "sonar.sources": ",".join(["frontend/src", *(str(module / "src/main") for module in modules)]),
        "sonar.tests": ",".join(["frontend/src", *(str(module / "src/test") for module in modules)]),
        "sonar.exclusions": "frontend/src/**/*.spec.ts,frontend/src/test-setup.ts",
        "sonar.test.inclusions": "frontend/src/**/*.spec.ts,frontend/src/test-setup.ts,backend/**/src/test/**/*",
        "sonar.java.binaries": ",".join(map(str, classes)),
        "sonar.java.test.binaries": ",".join(map(str, tests)),
        "sonar.java.libraries": ",".join(sorted(libraries)),
        "sonar.java.test.libraries": ",".join(sorted(libraries)),
        "sonar.coverageReportPaths": str(output / "java.xml"),
        "sonar.javascript.lcov.reportPaths": str(output / "frontend.lcov"),
    }
    (output / "scan.properties").write_text(
        "".join(f"{key}={value}\n" for key, value in props.items()), encoding="utf-8"
    )


if __name__ == "__main__":
    main()
