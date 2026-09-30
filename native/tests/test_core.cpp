#include "omnimind_core.h"
#include "omnimind_types.h"
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <fstream>
#include <vector>

static void test_init_shutdown() {
    std::cout << "[TEST] test_init_shutdown..." << std::endl;
    omnimind_status_t status = omnimind_init();
    assert(status == OMNIMIND_OK);

    // Double init should be safe (idempotent)
    status = omnimind_init();
    assert(status == OMNIMIND_OK);

    omnimind_shutdown();
    std::cout << "[PASS] test_init_shutdown" << std::endl;
}

static void test_status_strings() {
    std::cout << "[TEST] test_status_strings..." << std::endl;
    assert(strcmp(omnimind_status_str(OMNIMIND_OK), "OK") == 0);
    assert(strcmp(omnimind_status_str(OMNIMIND_ERROR_FILE_NOT_FOUND), "File not found") == 0);
    assert(strcmp(omnimind_status_str(OMNIMIND_ERROR_INVALID_GGUF), "Invalid GGUF file") == 0);
    assert(strcmp(omnimind_status_str(OMNIMIND_ERROR_CANCELLED), "Operation cancelled") == 0);
    std::cout << "[PASS] test_status_strings" << std::endl;
}

static void test_missing_file() {
    std::cout << "[TEST] test_missing_file..." << std::endl;
    omnimind_status_t status = omnimind_validate_gguf("non_existent_model_12345.gguf");
    assert(status == OMNIMIND_ERROR_FILE_NOT_FOUND);

    omnimind_model_metadata_t meta{};
    status = omnimind_extract_metadata("non_existent_model_12345.gguf", &meta);
    assert(status == OMNIMIND_ERROR_FILE_NOT_FOUND || status == OMNIMIND_ERROR_INVALID_ARGUMENT);

    omnimind_model_t* model = nullptr;
    status = omnimind_load_model("non_existent_model_12345.gguf", nullptr, &model);
    assert(status == OMNIMIND_ERROR_FILE_NOT_FOUND);
    assert(model == nullptr);
    std::cout << "[PASS] test_missing_file" << std::endl;
}

static void test_corrupted_file() {
    std::cout << "[TEST] test_corrupted_file..." << std::endl;
    const char* dummy_path = "corrupted_test.gguf";

    // 1. Zero-byte file
    {
        std::ofstream f(dummy_path, std::ios::binary | std::ios::trunc);
    }
    assert(omnimind_validate_gguf(dummy_path) == OMNIMIND_ERROR_INVALID_GGUF);

    // 2. Corrupt magic
    {
        std::ofstream f(dummy_path, std::ios::binary | std::ios::trunc);
        f.write("BAD!", 4);
    }
    assert(omnimind_validate_gguf(dummy_path) == OMNIMIND_ERROR_INVALID_GGUF);

    // 3. Valid magic "GGUF" but truncated version
    {
        std::ofstream f(dummy_path, std::ios::binary | std::ios::trunc);
        f.write("GGUF", 4);
    }
    assert(omnimind_validate_gguf(dummy_path) == OMNIMIND_ERROR_INVALID_GGUF);

    // 4. Invalid GGUF version (e.g. version 99)
    {
        std::ofstream f(dummy_path, std::ios::binary | std::ios::trunc);
        uint32_t magic = 0x46554747; // "GGUF" in little endian
        uint32_t bad_version = 99;
        f.write(reinterpret_cast<const char*>(&magic), 4);
        f.write(reinterpret_cast<const char*>(&bad_version), 4);
    }
    assert(omnimind_validate_gguf(dummy_path) == OMNIMIND_ERROR_INVALID_GGUF);

    std::remove(dummy_path);
    std::cout << "[PASS] test_corrupted_file" << std::endl;
}

static void test_backend_enumeration() {
    std::cout << "[TEST] test_backend_enumeration..." << std::endl;
    omnimind_backend_info_t backends[16];
    size_t count = 0;
    omnimind_status_t status = omnimind_get_available_backends(backends, 16, &count);
    assert(status == OMNIMIND_OK);
    assert(count >= 1);
    std::cout << "  Found " << count << " backends:" << std::endl;
    for (size_t i = 0; i < count; ++i) {
        std::cout << "    [" << i << "] " << backends[i].name
                  << " (" << (backends[i].is_accelerator ? "Accelerator" : "CPU") << "): "
                  << backends[i].description << std::endl;
    }
    std::cout << "[PASS] test_backend_enumeration" << std::endl;
}

int main() {
    std::cout << "========================================" << std::endl;
    std::cout << "Running OmniMind Native Core Tests" << std::endl;
    std::cout << "========================================" << std::endl;

    test_init_shutdown();
    test_status_strings();
    test_missing_file();
    test_corrupted_file();
    test_backend_enumeration();

    std::cout << "========================================" << std::endl;
    std::cout << "All Native Core Tests Passed Successfully!" << std::endl;
    std::cout << "========================================" << std::endl;
    return 0;
}
