#include <megaapi.h>
#include <condition_variable>
#include <cstdint>
#include <iostream>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>
#ifdef _WIN32
#include <fcntl.h>
#include <io.h>
#endif

using namespace mega;
namespace {
struct Failure { std::string code; };
uint32_t readInt() {
    uint32_t value = 0;
    for (int i = 0; i < 4; ++i) {
        int byte = std::cin.get();
        if (byte == EOF) throw Failure{"MEGA_PROTOCOL_ERROR"};
        value = (value << 8) | static_cast<unsigned char>(byte);
    }
    return value;
}
void writeInt(uint32_t value) {
    for (int i = 3; i >= 0; --i) std::cout.put(static_cast<char>((value >> (8 * i)) & 255));
}
std::vector<std::string> readRequest() {
    uint32_t count = readInt();
    if (count < 2 || count > 8) throw Failure{"MEGA_PROTOCOL_ERROR"};
    std::vector<std::string> fields;
    for (uint32_t i = 0; i < count; ++i) {
        uint32_t size = readInt();
        if (size > 16384) throw Failure{"MEGA_PROTOCOL_ERROR"};
        std::string field(size, '\0');
        std::cin.read(field.data(), size);
        if (!std::cin || field.find('\0') != std::string::npos) throw Failure{"MEGA_PROTOCOL_ERROR"};
        fields.push_back(std::move(field));
    }
    if (std::cin.peek() != EOF) throw Failure{"MEGA_PROTOCOL_ERROR"};
    return fields;
}
void respond(const std::vector<std::string>& fields) {
    writeInt(static_cast<uint32_t>(fields.size()));
    for (const auto& field : fields) {
        writeInt(static_cast<uint32_t>(field.size()));
        std::cout.write(field.data(), field.size());
    }
    std::cout.flush();
}
class Request final : public MegaRequestListener {
    std::mutex mutex;
    std::condition_variable changed;
    bool done = false;
public:
    int code = MegaError::API_EINTERNAL;
    MegaHandle handle = INVALID_HANDLE;
    void onRequestFinish(MegaApi*, MegaRequest* request, MegaError* error) override {
        std::lock_guard<std::mutex> lock(mutex);
        code = error->getErrorCode(); handle = request->getNodeHandle(); done = true;
        changed.notify_all();
    }
    int wait() {
        std::unique_lock<std::mutex> lock(mutex);
        // The Java parent enforces the whole-operation deadline by terminating this process.
        changed.wait(lock, [&] { return done; });
        return code;
    }
};
class Transfer final : public MegaTransferListener {
    std::mutex mutex;
    std::condition_variable changed;
    bool done = false;
public:
    int code = MegaError::API_EINTERNAL;
    MegaHandle handle = INVALID_HANDLE;
    void onTransferFinish(MegaApi*, MegaTransfer* transfer, MegaError* error) override {
        std::lock_guard<std::mutex> lock(mutex);
        code = error->getErrorCode(); handle = transfer->getNodeHandle(); done = true;
        changed.notify_all();
    }
    void wait() {
        std::unique_lock<std::mutex> lock(mutex);
        changed.wait(lock, [&] { return done; });
        if (code != MegaError::API_OK) throw Failure{"MEGA_STORAGE_ERROR"};
    }
};
std::string owned(char* value) {
    std::unique_ptr<char[]> holder(value);
    if (!holder || !holder[0]) throw Failure{"MEGA_STORAGE_ERROR"};
    return holder.get();
}
void require(int code, const char* failure) {
    if (code != MegaError::API_OK) throw Failure{failure};
}
std::vector<std::string> run(const std::vector<std::string>& f) {
    const std::string& operation = f[0];
    if (f[1].empty()) throw Failure{"MEGA_NOT_CONFIGURED"};
    MegaApi::setLogLevel(MegaApi::LOG_LEVEL_FATAL);
    // No disk cache: sessions are owned and encrypted by the Java application.
    MegaApi api(f[1].c_str(), static_cast<const char*>(nullptr), "ProjectFlow/1");
    Request login;
    if (operation == "login") {
        if (f.size() != 6) throw Failure{"MEGA_PROTOCOL_ERROR"};
        if (f[4].empty()) api.login(f[2].c_str(), f[3].c_str(), &login);
        else api.multiFactorAuthLogin(f[2].c_str(), f[3].c_str(), f[4].c_str(), &login);
        int result = login.wait();
        if (result == MegaError::API_EMFAREQUIRED) throw Failure{"MEGA_MFA_REQUIRED"};
        require(result, "MEGA_AUTHENTICATION_FAILED");
    } else {
        if ((operation == "upload" && f.size() != 7)
                || (operation == "download" && f.size() != 6)
                || (operation == "remove" && f.size() != 5)
                || (operation != "upload" && operation != "download" && operation != "remove"))
            throw Failure{"MEGA_PROTOCOL_ERROR"};
        api.fastLogin(f[2].c_str(), &login);
        require(login.wait(), "MEGA_ACCOUNT_UNAVAILABLE");
    }
    std::string account = owned(api.getMyUserHandle());
    if (operation != "login" && account != f[3]) throw Failure{"MEGA_ACCOUNT_UNAVAILABLE"};
    Request fetch;
    api.fetchNodes(&fetch);
    require(fetch.wait(), "MEGA_STORAGE_ERROR");
    if (operation == "login") {
        std::unique_ptr<MegaNode> root(api.getRootNode());
        if (!root) throw Failure{"MEGA_STORAGE_ERROR"};
        std::unique_ptr<MegaNode> folder(api.getChildNode(root.get(), f[5].c_str()));
        if (!folder) {
            Request create;
            api.createFolder(f[5].c_str(), root.get(), &create);
            require(create.wait(), "MEGA_STORAGE_ERROR");
            folder.reset(api.getNodeByHandle(create.handle));
        }
        if (!folder || !folder->isFolder()) throw Failure{"MEGA_STORAGE_ERROR"};
        return {"OK", account, owned(api.dumpSession()), owned(folder->getBase64Handle())};
    }
    std::unique_ptr<MegaNode> node(api.getNodeByHandle(MegaApi::base64ToHandle(f[4].c_str())));
    if (operation == "remove") {
        if (!node) return {"OK"};
        if (!node->isFile()) throw Failure{"MEGA_STORAGE_ERROR"};
        Request remove;
        api.remove(node.get(), &remove);
        require(remove.wait(), "MEGA_STORAGE_ERROR");
        return {"OK"};
    }
    if (!node) throw Failure{"MEGA_STORAGE_ERROR"};
    std::unique_ptr<MegaCancelToken> cancel(MegaCancelToken::createInstance());
    Transfer transfer;
    if (operation == "upload") {
        if (!node->isFolder()) throw Failure{"MEGA_STORAGE_ERROR"};
        MegaUploadOptions options;
        options.fileName = f[6];
        api.startUpload(f[5], node.get(), cancel.get(), &options, &transfer);
        transfer.wait();
        return {"OK", owned(MegaApi::handleToBase64(transfer.handle))};
    }
    if (!node->isFile() || node->getSize() > 5242880) throw Failure{"MEGA_STORAGE_ERROR"};
    api.startDownload(node.get(), f[5].c_str(), nullptr, nullptr, false, cancel.get(),
            MegaTransfer::COLLISION_CHECK_ASSUMEDIFFERENT,
            MegaTransfer::COLLISION_RESOLUTION_OVERWRITE, false, &transfer);
    transfer.wait();
    return {"OK"};
}
}
int main(int argc, char** argv) {
    if (argc == 2 && std::string(argv[1]) == "--version") {
        std::cout << "ProjectFlow MEGA bridge/1 SDK 10.22.0\n";
        return 0;
    }
    if (argc == 2 && std::string(argv[1]) == "--self-test") {
        std::string handle = owned(MegaApi::handleToBase64(MegaApi::base64ToHandle("AAAAAAAB")));
        if (handle != "AAAAAAAB") return 1;
        std::cout << "ProjectFlow MEGA bridge offline self-test OK\n";
        return 0;
    }
#ifdef _WIN32
    _setmode(_fileno(stdin), _O_BINARY);
    _setmode(_fileno(stdout), _O_BINARY);
#endif
    try { respond(run(readRequest())); }
    catch (const Failure& failure) { respond({failure.code}); }
    catch (...) { respond({"MEGA_STORAGE_ERROR"}); }
    return 0;
}
