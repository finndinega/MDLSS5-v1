#include <jni.h>
#include <string>
#include <cstdio>
#include <vulkan/vulkan.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_fjdei_mdlss5_MainActivity_nativeVulkanInfo(JNIEnv* env, jobject) {
    uint32_t version = VK_API_VERSION_1_0;
    auto enumerateVersion = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
        vkGetInstanceProcAddr(VK_NULL_HANDLE, "vkEnumerateInstanceVersion"));
    if (enumerateVersion) enumerateVersion(&version);
    VkApplicationInfo app{};
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "MDLSS5";
    app.apiVersion = version;
    VkInstanceCreateInfo create{};
    create.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    create.pApplicationInfo = &app;
    VkInstance instance = VK_NULL_HANDLE;
    VkResult result = vkCreateInstance(&create, nullptr, &instance);
    char buffer[256];
    if (result == VK_SUCCESS) {
        snprintf(buffer, sizeof(buffer), "Vulkan instance creation: SUCCESS\nLoader API: %u.%u.%u",
            VK_VERSION_MAJOR(version), VK_VERSION_MINOR(version), VK_VERSION_PATCH(version));
        vkDestroyInstance(instance, nullptr);
    } else {
        snprintf(buffer, sizeof(buffer), "Vulkan instance creation failed (VkResult %d). Driver/loader support needs investigation.",
            static_cast<int>(result));
    }
    return env->NewStringUTF(buffer);
}
