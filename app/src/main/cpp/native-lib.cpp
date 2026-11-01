#include <jni.h>
#include <vulkan/vulkan.h>
#include <cstdio>

extern "C" JNIEXPORT jstring JNICALL
Java_com_fjdei_mdlss5_MainActivity_nativeVulkanInfo(JNIEnv* env, jobject) {
    VkApplicationInfo app{};
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "MDLSS5 Gaming";
    app.apiVersion = VK_API_VERSION_1_0;
    VkInstanceCreateInfo create{};
    create.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    create.pApplicationInfo = &app;
    VkInstance instance = VK_NULL_HANDLE;
    if (vkCreateInstance(&create, nullptr, &instance) != VK_SUCCESS)
        return env->NewStringUTF("Vulkan instance creation failed; hardware acceleration cannot be confirmed.");
    uint32_t count = 0;
    if (vkEnumeratePhysicalDevices(instance, &count, nullptr) != VK_SUCCESS || count == 0) {
        vkDestroyInstance(instance, nullptr);
        return env->NewStringUTF("No Vulkan physical device was exposed by the driver.");
    }
    VkPhysicalDevice devices[8]{};
    uint32_t shown = count > 8 ? 8 : count;
    vkEnumeratePhysicalDevices(instance, &shown, devices);
    char report[2048];
    int used = snprintf(report, sizeof(report), "Vulkan devices detected: %u\n", count);
    for (uint32_t i = 0; i < shown && used < (int)sizeof(report) - 256; ++i) {
        VkPhysicalDeviceProperties props{};
        vkGetPhysicalDeviceProperties(devices[i], &props);
        VkPhysicalDeviceMemoryProperties memory{};
        vkGetPhysicalDeviceMemoryProperties(devices[i], &memory);
        VkDeviceSize localBytes = 0;
        for (uint32_t h = 0; h < memory.memoryHeapCount; ++h)
            if (memory.memoryHeaps[h].flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT)
                localBytes += memory.memoryHeaps[h].size;
        used += snprintf(report + used, sizeof(report) - used,
            "%s: %s | Vulkan %u.%u.%u | device-local heap %.0f MB\n",
            props.deviceType == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU ? "Integrated GPU" :
            props.deviceType == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU ? "Discrete GPU" : "GPU/device",
            props.deviceName, VK_VERSION_MAJOR(props.apiVersion),
            VK_VERSION_MINOR(props.apiVersion), VK_VERSION_PATCH(props.apiVersion),
            (double)localBytes / (1024.0 * 1024.0));
    }
    vkDestroyInstance(instance, nullptr);
    return env->NewStringUTF(report);
}
