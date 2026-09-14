// Multi-call command-line launcher for the AutoJs6 terminal.
//
// The AutoJs6 host links `$PREFIX/bin/{node,npm,npx,corepack,yarn,yarnpkg,pnpm,pnpx}`
// to this executable (packaged as `lib/<abi>/libnodexe.so` so the installer extracts
// it next to libnode.so). The command is chosen from `basename(argv[0])`: a plain
// `node` invocation forwards argv unchanged, while npm / corepack commands insert the
// matching CLI script from `$AUTOJS6_NODE_CLI_ROOT` (the extracted npm + corepack asset
// archive, see docs/nodejs/TERMINAL.md) before the user arguments.
//
// zh-CN: AutoJs6 终端的多入口命令行启动器. 宿主把 `$PREFIX/bin/{node,npm,...}` 链接到本
// 可执行文件 (打包名 `lib/<abi>/libnodexe.so`, 安装器会把它解压到 libnode.so 旁边). 根据
// `basename(argv[0])` 选择命令: `node` 原样转发 argv, npm / corepack 命令则在用户参数前插入
// `$AUTOJS6_NODE_CLI_ROOT` 中对应的 CLI 脚本 (解压后的 npm + corepack 资产, 见 docs/nodejs/TERMINAL.md).

#include <node.h>

#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include <unistd.h>

namespace {

constexpr const char* kCliRootVariable = "AUTOJS6_NODE_CLI_ROOT";

struct Command {
    const char* name;
    const char* script;
};

// Keep in sync with NODE_CLI_COMMANDS in AndroidManifest.xml and the archive layout
// produced by tools/nodejs/cli/build-node-cli-archive.py (paths relative to the archive root).
constexpr Command kCommands[] = {
        {"npm", "npm/bin/npm-cli.js"},
        {"npx", "npm/bin/npx-cli.js"},
        {"corepack", "corepack/dist/corepack.js"},
        {"yarn", "corepack/dist/yarn.js"},
        {"yarnpkg", "corepack/dist/yarnpkg.js"},
        {"pnpm", "corepack/dist/pnpm.js"},
        {"pnpx", "corepack/dist/pnpx.js"},
};

std::string commandName(const char* argv0) {
    std::string name = argv0 == nullptr ? std::string() : std::string(argv0);
    const std::string::size_type slash = name.find_last_of('/');
    if (slash != std::string::npos) {
        name.erase(0, slash + 1);
    }
    // `libnodexe.so` and any other `lib*.so` spelling behave like `node`.
    if (name.size() > 6 && name.compare(0, 3, "lib") == 0 && name.compare(name.size() - 3, 3, ".so") == 0) {
        name = name.substr(3, name.size() - 6);
    }
    return name;
}

const Command* findCommand(const std::string& name) {
    for (const Command& command : kCommands) {
        if (name == command.name) {
            return &command;
        }
    }
    return nullptr;
}

// libuv's uv_setup_args() expects argv strings in one contiguous block so that it can
// reuse the memory for the process title; rebuild argv that way after inserting the script.
int startWithArguments(const std::vector<std::string>& arguments) {
    std::size_t total = 0;
    for (const std::string& argument : arguments) {
        total += argument.size() + 1;
    }
    std::vector<char> block(total);
    std::vector<char*> argv;
    argv.reserve(arguments.size() + 1);
    char* cursor = block.data();
    for (const std::string& argument : arguments) {
        std::memcpy(cursor, argument.c_str(), argument.size() + 1);
        argv.push_back(cursor);
        cursor += argument.size() + 1;
    }
    argv.push_back(nullptr);
    return node::Start(static_cast<int>(arguments.size()), argv.data());
}

}  // namespace

int main(int argc, char* argv[]) {
    const std::string name = commandName(argc > 0 ? argv[0] : nullptr);
    const Command* command = findCommand(name);
    if (command == nullptr) {
        return node::Start(argc, argv);
    }
    const char* root = std::getenv(kCliRootVariable);
    if (root == nullptr || *root == '\0') {
        std::fprintf(stderr,
                     "%s: %s is not set; the AutoJs6 terminal exports it once the npm / corepack "
                     "archive of the Node.js Runtime plugin has been installed.\n",
                     name.c_str(), kCliRootVariable);
        return 1;
    }
    std::string script = std::string(root) + "/" + command->script;
    if (access(script.c_str(), R_OK) != 0) {
        std::fprintf(stderr, "%s: cannot read %s (%s); reinstall the npm / corepack archive from the "
                             "Node.js Runtime plugin.\n",
                     name.c_str(), script.c_str(), std::strerror(errno));
        return 1;
    }
    std::vector<std::string> arguments;
    arguments.reserve(static_cast<std::size_t>(argc) + 1);
    arguments.emplace_back(argv[0]);
    arguments.push_back(script);
    for (int i = 1; i < argc; ++i) {
        arguments.emplace_back(argv[i]);
    }
    return startWithArguments(arguments);
}
