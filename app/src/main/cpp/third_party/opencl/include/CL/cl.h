#ifndef __OPENCL_CL_H
#define __OPENCL_CL_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef int32_t cl_int;
typedef uint32_t cl_uint;
typedef uint64_t cl_ulong;
typedef uint32_t cl_bitfield;
typedef cl_uint cl_bool;
typedef uint32_t cl_version;

typedef cl_bitfield cl_device_type;
typedef cl_bitfield cl_mem_flags;
typedef cl_uint cl_context_properties;
typedef cl_uint cl_device_info;
typedef cl_uint cl_platform_info;
typedef cl_uint cl_program_info;
typedef cl_uint cl_program_build_info;
typedef cl_uint cl_kernel_info;
typedef cl_uint cl_command_queue_properties;
typedef cl_uint cl_profiling_info;
typedef cl_uint cl_channel_type;
typedef cl_bitfield cl_device_svm_capabilities;

typedef struct _cl_platform_id *    cl_platform_id;
typedef struct _cl_device_id *      cl_device_id;
typedef struct _cl_context *        cl_context;
typedef struct _cl_command_queue *  cl_command_queue;
typedef struct _cl_mem *            cl_mem;
typedef struct _cl_program *        cl_program;
typedef struct _cl_kernel *         cl_kernel;
typedef struct _cl_event *          cl_event;

#define CL_NAME_VERSION_MAX_NAME_SIZE 64
typedef struct _cl_name_version {
    cl_version version;
    char name[CL_NAME_VERSION_MAX_NAME_SIZE];
} cl_name_version;

#define CL_VERSION_MAJOR_BITS (10)
#define CL_VERSION_MINOR_BITS (10)
#define CL_VERSION_PATCH_BITS (12)

#define CL_VERSION_MAJOR_MASK ((1U << CL_VERSION_MAJOR_BITS) - 1)
#define CL_VERSION_MINOR_MASK ((1U << CL_VERSION_MINOR_BITS) - 1)
#define CL_VERSION_PATCH_MASK ((1U << CL_VERSION_PATCH_BITS) - 1)

#define CL_VERSION_MAJOR(v) ((v) >> (CL_VERSION_MINOR_BITS + CL_VERSION_PATCH_BITS))
#define CL_VERSION_MINOR(v) (((v) >> CL_VERSION_PATCH_BITS) & CL_VERSION_MINOR_MASK)
#define CL_VERSION_PATCH(v) ((v) & CL_VERSION_PATCH_MASK)

#define CL_SUCCESS                                  0
#define CL_DEVICE_NOT_FOUND                         -1
#define CL_BUILD_PROGRAM_FAILURE                    -11

#define CL_TRUE                                     1
#define CL_FALSE                                    0

#define CL_PLATFORM_VERSION                         0x0903
#define CL_DEVICE_NAME                              0x102B
#define CL_DEVICE_VERSION                           0x102F
#define CL_DRIVER_VERSION                           0x102D
#define CL_DEVICE_PLATFORM                          0x1031
#define CL_DEVICE_TYPE                              0x1000
#define CL_DEVICE_OPENCL_C_ALL_VERSIONS             0x1065
#define CL_DEVICE_OPENCL_C_VERSION                  0x103D

#define CL_DEVICE_TYPE_GPU                          (1 << 2)

#define CL_PROGRAM_NUM_DEVICES                      0x1162
#define CL_PROGRAM_BINARY_SIZES                     0x1165
#define CL_PROGRAM_BINARIES                         0x1166
#define CL_PROGRAM_BUILD_LOG                        0x1183

#define CL_PROFILING_COMMAND_QUEUED                 0x1280
#define CL_PROFILING_COMMAND_SUBMIT                 0x1281
#define CL_PROFILING_COMMAND_START                  0x1282
#define CL_PROFILING_COMMAND_END                    0x1283
#define CL_PROFILING_COMMAND_COMPLETE               0x1284

#define CL_MEM_READ_WRITE                           (1 << 0)
#define CL_MEM_WRITE_ONLY                           (1 << 1)
#define CL_MEM_READ_ONLY                            (1 << 2)
#define CL_MEM_COPY_HOST_PTR                        (1 << 5)

#define CL_FLOAT                                    0x10DE

#define CL_CALLBACK

extern cl_int clGetPlatformIDs(cl_uint, cl_platform_id *, cl_uint *);
extern cl_int clGetPlatformInfo(cl_platform_id, cl_platform_info, size_t, void *, size_t *);
extern cl_int clGetDeviceIDs(cl_platform_id, cl_device_type, cl_uint, cl_device_id *, cl_uint *);
extern cl_int clGetDeviceInfo(cl_device_id, cl_device_info, size_t, void *, size_t *);
extern cl_context clCreateContext(const cl_context_properties *, cl_uint, const cl_device_id *, void (CL_CALLBACK *)(const char *, const void *, size_t, void *), void *, cl_int *);
extern cl_command_queue clCreateCommandQueue(cl_context, cl_device_id, cl_command_queue_properties, cl_int *);
extern cl_mem clCreateBuffer(cl_context, cl_mem_flags, size_t, void *, cl_int *);
extern cl_program clCreateProgramWithSource(cl_context, cl_uint, const char **, const size_t *, cl_int *);
extern cl_program clCreateProgramWithBinary(cl_context, cl_uint, const cl_device_id *, const size_t *, const unsigned char **, cl_int *, cl_int *);
extern cl_int clBuildProgram(cl_program, cl_uint, const cl_device_id *, const char *, void (CL_CALLBACK *)(cl_program, void *), void *);
extern cl_int clGetProgramInfo(cl_program, cl_program_info, size_t, void *, size_t *);
extern cl_int clGetProgramBuildInfo(cl_program, cl_device_id, cl_program_build_info, size_t, void *, size_t *);
extern cl_kernel clCreateKernel(cl_program, const char *, cl_int *);
extern cl_int clSetKernelArg(cl_kernel, cl_uint, size_t, const void *);
extern cl_int clEnqueueNDRangeKernel(cl_command_queue, cl_kernel, cl_uint, const size_t *, const size_t *, const size_t *, cl_uint, const cl_event *, cl_event *);
extern cl_int clEnqueueReadBuffer(cl_command_queue, cl_mem, cl_bool, size_t, size_t, void *, cl_uint, const cl_event *, cl_event *);
extern cl_int clEnqueueWriteBuffer(cl_command_queue, cl_mem, cl_bool, size_t, size_t, const void *, cl_uint, const cl_event *, cl_event *);
extern cl_int clWaitForEvents(cl_uint, const cl_event *);
extern cl_int clGetEventProfilingInfo(cl_event, cl_profiling_info, size_t, void *, size_t *);
extern cl_int clFinish(cl_command_queue);
extern cl_int clReleaseEvent(cl_event);
extern cl_int clReleaseKernel(cl_kernel);
extern cl_int clReleaseProgram(cl_program);
extern cl_int clReleaseMemObject(cl_mem);
extern cl_int clReleaseCommandQueue(cl_command_queue);
extern cl_int clReleaseContext(cl_context);

#ifdef __cplusplus
}
#endif

#endif
