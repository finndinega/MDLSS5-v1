#include <jni.h>
#include <vulkan/vulkan.h>
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <vector>
#include "mdlss5_shader.h"

extern "C" JNIEXPORT jstring JNICALL Java_com_fjdei_mdlss5_MainActivity_nativeVulkanInfo(JNIEnv* env,jobject){
 VkApplicationInfo a{};a.sType=VK_STRUCTURE_TYPE_APPLICATION_INFO;a.pApplicationName="MDLSS5";a.apiVersion=VK_API_VERSION_1_0;
 VkInstanceCreateInfo c{};c.sType=VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;c.pApplicationInfo=&a;VkInstance in=VK_NULL_HANDLE;
 if(vkCreateInstance(&c,nullptr,&in)!=VK_SUCCESS)return env->NewStringUTF("Vulkan instance creation failed.");
 uint32_t n=0;vkEnumeratePhysicalDevices(in,&n,nullptr);if(!n){vkDestroyInstance(in,nullptr);return env->NewStringUTF("No Vulkan GPU exposed.");}
 std::vector<VkPhysicalDevice>d(n);vkEnumeratePhysicalDevices(in,&n,d.data());char b[2048];int u=snprintf(b,sizeof(b),"Vulkan devices detected: %u\n",n);
 for(uint32_t i=0;i<n&&u<(int)sizeof(b)-256;i++){VkPhysicalDeviceProperties p{};vkGetPhysicalDeviceProperties(d[i],&p);u+=snprintf(b+u,sizeof(b)-u,"%s | Vulkan %u.%u.%u\n",p.deviceName,VK_VERSION_MAJOR(p.apiVersion),VK_VERSION_MINOR(p.apiVersion),VK_VERSION_PATCH(p.apiVersion));}
 vkDestroyInstance(in,nullptr);return env->NewStringUTF(b);
}
struct GpuBuffer { VkBuffer buffer=VK_NULL_HANDLE; VkDeviceMemory memory=VK_NULL_HANDLE; void* mapped=nullptr; };
static bool makeBuffer(VkDevice dev,VkPhysicalDevice phys,VkDeviceSize size,GpuBuffer& out){
 VkBufferCreateInfo bi{};bi.sType=VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;bi.size=size;bi.usage=VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;bi.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
 if(vkCreateBuffer(dev,&bi,nullptr,&out.buffer)!=VK_SUCCESS)return false;
 VkMemoryRequirements req{};vkGetBufferMemoryRequirements(dev,out.buffer,&req);VkPhysicalDeviceMemoryProperties mp{};vkGetPhysicalDeviceMemoryProperties(phys,&mp);
 uint32_t type=UINT32_MAX;for(uint32_t i=0;i<mp.memoryTypeCount;i++)if((req.memoryTypeBits&(1u<<i))&&(mp.memoryTypes[i].propertyFlags&VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)&&(mp.memoryTypes[i].propertyFlags&VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)){type=i;break;}
 if(type==UINT32_MAX)return false;VkMemoryAllocateInfo ai{};ai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;ai.allocationSize=req.size;ai.memoryTypeIndex=type;
 if(vkAllocateMemory(dev,&ai,nullptr,&out.memory)!=VK_SUCCESS)return false;
 if(vkBindBufferMemory(dev,out.buffer,out.memory,0)!=VK_SUCCESS)return false;
 return vkMapMemory(dev,out.memory,0,size,0,&out.mapped)==VK_SUCCESS;
}
static void freeBuffer(VkDevice d,GpuBuffer& b){if(b.mapped)vkUnmapMemory(d,b.memory);if(b.buffer)vkDestroyBuffer(d,b.buffer,nullptr);if(b.memory)vkFreeMemory(d,b.memory,nullptr);b={};}
extern "C" JNIEXPORT jintArray JNICALL Java_com_fjdei_mdlss5_GraphicsLabActivity_nativeEnhance(JNIEnv*e,jobject,jintArray input,jint w,jint h,jint scale,jfloat sharp,jfloat contrast){
 if(!input||w<2||h<2||(scale!=2&&scale!=3))return nullptr;
 const jsize count=e->GetArrayLength(input);const int64_t pixels=(int64_t)w*h,outputPixels=pixels*scale*scale;
 if(count!=pixels||outputPixels>12000000)return nullptr;
 std::vector<uint32_t> src((size_t)pixels),dst((size_t)outputPixels);
 e->GetIntArrayRegion(input,0,count,reinterpret_cast<jint*>(src.data()));if(e->ExceptionCheck())return nullptr;
 VkInstance instance=VK_NULL_HANDLE;VkDevice device=VK_NULL_HANDLE;VkPipeline pipeline=VK_NULL_HANDLE;VkPipelineLayout layout=VK_NULL_HANDLE;VkDescriptorSetLayout dsl=VK_NULL_HANDLE;VkDescriptorPool pool=VK_NULL_HANDLE;VkCommandPool cp=VK_NULL_HANDLE;VkFence fence=VK_NULL_HANDLE;VkShaderModule module=VK_NULL_HANDLE;GpuBuffer in,out;bool ok=false;
 VkApplicationInfo app{};app.sType=VK_STRUCTURE_TYPE_APPLICATION_INFO;app.pApplicationName="MDLSS5 Compute";app.apiVersion=VK_API_VERSION_1_0;
 VkInstanceCreateInfo ici{};ici.sType=VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;ici.pApplicationInfo=&app;
 if(vkCreateInstance(&ici,nullptr,&instance)!=VK_SUCCESS)goto cleanup;
 {uint32_t n=0;if(vkEnumeratePhysicalDevices(instance,&n,nullptr)!=VK_SUCCESS||!n)goto cleanup;std::vector<VkPhysicalDevice>devs(n);vkEnumeratePhysicalDevices(instance,&n,devs.data());VkPhysicalDevice phys=VK_NULL_HANDLE;uint32_t qf=UINT32_MAX;
 for(auto candidate:devs){uint32_t qc=0;vkGetPhysicalDeviceQueueFamilyProperties(candidate,&qc,nullptr);std::vector<VkQueueFamilyProperties>q(qc);vkGetPhysicalDeviceQueueFamilyProperties(candidate,&qc,q.data());for(uint32_t i=0;i<qc;i++)if(q[i].queueFlags&VK_QUEUE_COMPUTE_BIT){phys=candidate;qf=i;break;}if(phys)break;}if(!phys)goto cleanup;
 float priority=1.f;VkDeviceQueueCreateInfo qci{};qci.sType=VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;qci.queueFamilyIndex=qf;qci.queueCount=1;qci.pQueuePriorities=&priority;VkDeviceCreateInfo dci{};dci.sType=VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;dci.queueCreateInfoCount=1;dci.pQueueCreateInfos=&qci;if(vkCreateDevice(phys,&dci,nullptr,&device)!=VK_SUCCESS)goto cleanup;
 VkQueue queue=VK_NULL_HANDLE;vkGetDeviceQueue(device,qf,0,&queue);
 if(!makeBuffer(device,phys,(VkDeviceSize)src.size()*4,in)||!makeBuffer(device,phys,(VkDeviceSize)dst.size()*4,out))goto cleanup;
 memcpy(in.mapped,src.data(),src.size()*4);
 VkShaderModuleCreateInfo sm{};sm.sType=VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;sm.codeSize=MDLSS5_SPV_SIZE;sm.pCode=MDLSS5_SPV;if(vkCreateShaderModule(device,&sm,nullptr,&module)!=VK_SUCCESS)goto cleanup;
 VkDescriptorSetLayoutBinding binds[2]{};for(uint32_t i=0;i<2;i++){binds[i].binding=i;binds[i].descriptorType=VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;binds[i].descriptorCount=1;binds[i].stageFlags=VK_SHADER_STAGE_COMPUTE_BIT;}
 VkDescriptorSetLayoutCreateInfo dl{};dl.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;dl.bindingCount=2;dl.pBindings=binds;if(vkCreateDescriptorSetLayout(device,&dl,nullptr,&dsl)!=VK_SUCCESS)goto cleanup;
 VkPushConstantRange pcr{};pcr.stageFlags=VK_SHADER_STAGE_COMPUTE_BIT;pcr.offset=0;pcr.size=sizeof(int)*3+sizeof(float)*2;
 VkPipelineLayoutCreateInfo pli{};pli.sType=VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;pli.setLayoutCount=1;pli.pSetLayouts=&dsl;pli.pushConstantRangeCount=1;pli.pPushConstantRanges=&pcr;if(vkCreatePipelineLayout(device,&pli,nullptr,&layout)!=VK_SUCCESS)goto cleanup;
 VkPipelineShaderStageCreateInfo stage{};stage.sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;stage.stage=VK_SHADER_STAGE_COMPUTE_BIT;stage.module=module;stage.pName="main";VkComputePipelineCreateInfo pci{};pci.sType=VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;pci.stage=stage;pci.layout=layout;if(vkCreateComputePipelines(device,VK_NULL_HANDLE,1,&pci,nullptr,&pipeline)!=VK_SUCCESS)goto cleanup;
 VkDescriptorPoolSize ps{};ps.type=VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;ps.descriptorCount=2;VkDescriptorPoolCreateInfo dpi{};dpi.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;dpi.maxSets=1;dpi.poolSizeCount=1;dpi.pPoolSizes=&ps;if(vkCreateDescriptorPool(device,&dpi,nullptr,&pool)!=VK_SUCCESS)goto cleanup;
 VkDescriptorSetAllocateInfo dai{};dai.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;dai.descriptorPool=pool;dai.descriptorSetCount=1;dai.pSetLayouts=&dsl;VkDescriptorSet set;if(vkAllocateDescriptorSets(device,&dai,&set)!=VK_SUCCESS)goto cleanup;
 VkDescriptorBufferInfo dbi[2]={{in.buffer,0,VK_WHOLE_SIZE},{out.buffer,0,VK_WHOLE_SIZE}};VkWriteDescriptorSet wr[2]{};for(uint32_t i=0;i<2;i++){wr[i].sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;wr[i].dstSet=set;wr[i].dstBinding=i;wr[i].descriptorCount=1;wr[i].descriptorType=VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;wr[i].pBufferInfo=&dbi[i];}vkUpdateDescriptorSets(device,2,wr,0,nullptr);
 VkCommandPoolCreateInfo cpi{};cpi.sType=VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;cpi.queueFamilyIndex=qf;if(vkCreateCommandPool(device,&cpi,nullptr,&cp)!=VK_SUCCESS)goto cleanup;
 VkCommandBufferAllocateInfo cai{};cai.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;cai.commandPool=cp;cai.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY;cai.commandBufferCount=1;VkCommandBuffer cmd;if(vkAllocateCommandBuffers(device,&cai,&cmd)!=VK_SUCCESS)goto cleanup;
 VkCommandBufferBeginInfo cbi{};cbi.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;if(vkBeginCommandBuffer(cmd,&cbi)!=VK_SUCCESS)goto cleanup;
 vkCmdBindPipeline(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,pipeline);vkCmdBindDescriptorSets(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,layout,0,1,&set,0,nullptr);
 struct Params{int width,height,scale;float sharpness,contrast;} params{w,h,scale,std::clamp((float)sharp,0.f,2.f),std::clamp((float)contrast,.5f,1.8f)};
 vkCmdPushConstants(cmd,layout,VK_SHADER_STAGE_COMPUTE_BIT,0,sizeof(params),&params);vkCmdDispatch(cmd,(uint32_t)(w*scale+15)/16,(uint32_t)(h*scale+15)/16,1);
 if(vkEndCommandBuffer(cmd)!=VK_SUCCESS)goto cleanup;VkFenceCreateInfo fci{};fci.sType=VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;if(vkCreateFence(device,&fci,nullptr,&fence)!=VK_SUCCESS)goto cleanup;
 VkSubmitInfo si{};si.sType=VK_STRUCTURE_TYPE_SUBMIT_INFO;si.commandBufferCount=1;si.pCommandBuffers=&cmd;if(vkQueueSubmit(queue,1,&si,fence)!=VK_SUCCESS||vkWaitForFences(device,1,&fence,VK_TRUE,30000000000ull)!=VK_SUCCESS)goto cleanup;
 memcpy(dst.data(),out.mapped,dst.size()*4);ok=true;
 }
 cleanup:
 if(device){vkDeviceWaitIdle(device);if(fence)vkDestroyFence(device,fence,nullptr);if(cp)vkDestroyCommandPool(device,cp,nullptr);if(pool)vkDestroyDescriptorPool(device,pool,nullptr);if(pipeline)vkDestroyPipeline(device,pipeline,nullptr);if(layout)vkDestroyPipelineLayout(device,layout,nullptr);if(dsl)vkDestroyDescriptorSetLayout(device,dsl,nullptr);if(module)vkDestroyShaderModule(device,module,nullptr);freeBuffer(device,in);freeBuffer(device,out);vkDestroyDevice(device,nullptr);}
 if(instance)vkDestroyInstance(instance,nullptr);if(!ok)return nullptr;
 jintArray result=e->NewIntArray((jsize)dst.size());if(result)e->SetIntArrayRegion(result,0,(jsize)dst.size(),reinterpret_cast<const jint*>(dst.data()));return result;
}