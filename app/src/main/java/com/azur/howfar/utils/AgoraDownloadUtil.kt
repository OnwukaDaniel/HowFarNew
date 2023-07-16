package com.azur.howfar.utils

import android.content.Context
import android.content.Intent
import com.azur.howfar.services.AgoraDownloadService
import com.google.gson.Gson
import java.io.File

class AgoraDownloadUtil(val context: Context) {
    var abiFile: File = File("${context.filesDir}/AgoraDownload/Architecture/")
    var abiFileLock: File = File("${context.filesDir}/AgoraDownload/ArchitectureLock/")
    private var listOfFiles = arrayListOf<ArchitectureClass>()
    var toDownload = arrayListOf<ArchitectureClass>()

    init {
        if (!abiFile.exists()) {
            abiFile.mkdirs()
        }
        if (!abiFileLock.exists()) {
            abiFileLock.mkdirs()
        }
    }

    fun checkAgoraFiles(architecture: String): Boolean {
        listOfFiles = when (architecture) {
            "arm64-v8a" -> arm64v8a
            "armeabi-v7a" -> armeabiv7a
            "x86" -> x86
            else -> x86_64
        }
        val names = arrayListOf<String>()
        for (i in listOfFiles) names.add(i.fileName.replace(".so", ""))

        if (abiFile.list()!!.isEmpty()) {
            toDownload = listOfFiles
            for (i in listOfFiles) File(abiFileLock, "${i.fileName}.lock").createNewFile()
            return false
        }
        if (abiFileLock.list()!!.isNotEmpty()) {
            for (i in abiFileLock.list()!!) {
                val incomplete = i.replace(".lock", "")
                toDownload.add(listOfFiles[names.indexOf(incomplete)])
            }
            return false
        } else {
            return true
        }
    }

    fun downloadFiles() {
        val intent = Intent(context, AgoraDownloadService::class.java)
        val json = Gson().toJson(toDownload)
        intent.putExtra("files", json)
        context.startForegroundService(intent)
    }

    private val arm64v8a = arrayListOf(
        ArchitectureClass(
            "libagora-fdkaac", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora-fdkaac.so?alt=media&token=098b649d-c6e2-4c7c-bfab-354cbdd513e0"
        ),
        ArchitectureClass(
            "libagora-ffmpeg", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot.com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora-ffmpeg" +
                    ".so?alt=media&token=75559ba3-8031-43fb-bad7-8d012914cc55"
        ),
        ArchitectureClass(
            "libagora-rtc-sdk", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora-rtc-sdk.so?alt=media&token=1078ac5b-cd97-4ee1-a534-27720a20d13d"
        ),
        ArchitectureClass(
            "libagora-soundtouch", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora-soundtouch.so?alt=media&token=328371fc-791a-4b3c-b368-9470d5f5ed1a"
        ),
        ArchitectureClass(
            "libagora_ai_echo_cancellation_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora_ai_echo_cancellation_extension" +
                    ".so?alt=media&token=fa96db71-5708-4334-a710-16c02da83900"
        ),
        ArchitectureClass(
            "libagora_ai_noise_suppression_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora_ai_noise_suppression_extension" +
                    ".so?alt=media&token=cb11959d-7544-459e-bb57-40159193034e"
        ),
        ArchitectureClass(
            "libagora_audio_beauty_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora_audio_beauty_extension" +
                    ".so?alt=media&token=3a8e6ad9-97de-40a8-ba77-3769bf951243"
        ),
        ArchitectureClass(
            "libagora_clear_vision_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora_clear_vision_extension" +
                    ".so?alt=media&token=39be762b-4f65-4081-ba06-e8e3007b0d36"
        ),
        ArchitectureClass(
            "libagora_content_inspect_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora_content_inspect_extension" +
                    ".so?alt=media&token=d3f21ab0-946a-4e88-a4e0-9a7969f6120b"
        ),
        ArchitectureClass(
            "libagora_dav1d", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora_dav1d" +
                    ".so?alt=media&token=40937d68-8fa7-4d9a-ac58-37b838c9e09a"
        ),
        ArchitectureClass(
            "libagora_drm_loader_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora_drm_loader_extension" +
                    ".so?alt=media&token=c2cff831-01d1-4388-8c9d-6d1fc2c310c6"
        ),
        ArchitectureClass(
            "libagora_screen_capture_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora_screen_capture_extension" +
                    ".so?alt=media&token=1584bb95-d635-45d3-99be-e423262caa9a"
        ),
        ArchitectureClass(
            "libagora_segmentation_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora_segmentation_extension" +
                    ".so?alt=media&token=74e3ac1b-f795-4eb0-83f5-64138adfe13d"
        ),
        ArchitectureClass(
            "libagora_spatial_audio_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora_spatial_audio_extension" +
                    ".so?alt=media&token=29173e3b-49f9-44ef-9693-9f2491edc7bc"
        ),
        ArchitectureClass(
            "libagora_super_resolution_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora_super_resolution_extension" +
                    ".so?alt=media&token=8fdb1e02-0b2f-4d19-95c2-c7f9a05cba8b"
        ),
        ArchitectureClass(
            "libagora_udrm3_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora_udrm3_extension" +
                    ".so?alt=media&token=9b53a530-9c78-4620-b8a3-6ce7288ebda4"
        ),
        ArchitectureClass(
            "libagora_video_quality_analyzer_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farm64-v8a%2Flibagora_video_quality_analyzer_extension" +
                    ".so?alt=media&token=4f6f4705-5f75-4c1d-a742-8c3fdeabcc78"
        ),
    )

    private val armeabiv7a = arrayListOf(
        ArchitectureClass(
            "libagora-fdkaac", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora-fdkaac.so?alt=media&token=9a2391bb-635d-490c-bbe7-dd957a19d14e"
        ),
        ArchitectureClass(
            "libagora-ffmpeg", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora-ffmpeg.so?alt=media&token=07c43542-03c5-4ead-b02e-2668057c5bd2"
        ),
        ArchitectureClass(
            "libagora-rtc-sdk", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora-rtc-sdk.so?alt=media&token=030440c9-3209-444e-abe7-db78d0f77e70"
        ),
        ArchitectureClass(
            "libagora-soundtouch", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora-soundtouch.so?alt=media&token=08b876b9-1c68-47a3-9895-c1195a5d69d8"
        ),
        ArchitectureClass(
            "libagora_ai_echo_cancellation_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora_ai_echo_cancellation_extension" +
                    ".so?alt=media&token=a8385d36-ffd4-43d1-914d-7cbd2fd19d76"
        ),
        ArchitectureClass(
            "libagora_ai_noise_suppression_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora_ai_noise_suppression_extension" +
                    ".so?alt=media&token=36fa96c6-ea8f-4f8b-a5f1-b6dab60259ba"
        ),
        ArchitectureClass(
            "libagora_audio_beauty_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora_audio_beauty_extension" +
                    ".so?alt=media&token=380ec315-98b2-4831-9ff7-2f7939b77e4e"
        ),
        ArchitectureClass(
            "libagora_clear_vision_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora_clear_vision_extension" +
                    ".so?alt=media&token=e955ce9c-4432-4b5e-8097-5c8c4efbe410"
        ),
        ArchitectureClass(
            "libagora_content_inspect_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora_content_inspect_extension" +
                    ".so?alt=media&token=68463eba-2e95-4d07-9f63-37c3a3039677"
        ),
        ArchitectureClass(
            "libagora_dav1d", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora_dav1d" +
                    ".so?alt=media&token=46af9299-a147-4c4c-87b1-a1b9b710d8aa"
        ),
        ArchitectureClass(
            "libagora_drm_loader_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora_drm_loader_extension" +
                    ".so?alt=media&token=e2faac5e-cb91-4478-906e-5db6fb32e91a"
        ),
        ArchitectureClass(
            "libagora_screen_capture_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora_screen_capture_extension" +
                    ".so?alt=media&token=9edbe779-666a-4285-b8ba-34e204925f4d"
        ),
        ArchitectureClass(
            "libagora_segmentation_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora_segmentation_extension" +
                    ".so?alt=media&token=ae447917-f02f-431a-869f-83e8c1b3c1b7"
        ),
        ArchitectureClass(
            "libagora_spatial_audio_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora_spatial_audio_extension" +
                    ".so?alt=media&token=b3b37400-b674-496a-82a5-ee991722a1e1"
        ),
        ArchitectureClass(
            "libagora_super_resolution_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora_super_resolution_extension" +
                    ".so?alt=media&token=bf91b0b7-0086-4bff-adb6-44e8e36a2320"
        ),
        ArchitectureClass(
            "libagora_udrm3_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora_udrm3_extension" +
                    ".so?alt=media&token=30602044-841b-42bc-89cb-4ad4479a2215"
        ),
        ArchitectureClass(
            "libagora_video_quality_analyzer_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Farmeabi-v7a%2Flibagora_video_quality_analyzer_extension" +
                    ".so?alt=media&token=fa3f15db-a032-40bd-a4e7-a6517822d844"
        ),
    )

    private val x86 = arrayListOf(
        ArchitectureClass(
            "libagora-fdkaac", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot.com/o/AGORA%20FILES%2Fx86%2Flibagora-fdkaac" +
                    ".so?alt=media&token=c5194a6a-b5b3-45c7-af63-ce80c025be5f"
        ),
        ArchitectureClass(
            "libagora-ffmpeg", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot.com/o/AGORA%20FILES%2Fx86%2Flibagora-ffmpeg" +
                    ".so?alt=media&token=13ed0d7b-d4a1-41f2-b8bc-86c89184bb62"
        ),
        ArchitectureClass(
            "libagora-rtc-sdk", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot.com/o/AGORA%20FILES%2Fx86%2Flibagora-rtc-sdk" +
                    ".so?alt=media&token=d97dc263-43c7-46f4-86f2-0a029d15b602"
        ),
        ArchitectureClass(
            "libagora-soundtouch", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot.com/o/AGORA%20FILES%2Fx86%2Flibagora-soundtouch" +
                    ".so?alt=media&token=e372eb38-deb2-4976-b8ff-dfddb0713309"
        ),
        ArchitectureClass(
            "libagora_ai_echo_cancellation_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86%2Flibagora_ai_echo_cancellation_extension" +
                    ".so?alt=media&token=bd78f90d-5848-45b6-9cf2-4289d05a1a08"
        ),
        ArchitectureClass(
            "libagora_ai_noise_suppression_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86%2Flibagora_ai_noise_suppression_extension.so?alt=media&token=a5827b56-6860-454e-9f02-1816e8aad20b"
        ),
        ArchitectureClass(
            "libagora_audio_beauty_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86%2Flibagora_audio_beauty_extension.so?alt=media&token=ace830ba-4c8c-4250-b0e5-7d1b295556c8"
        ),
        ArchitectureClass(
            "libagora_clear_vision_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86%2Flibagora_clear_vision_extension.so?alt=media&token=36aa0da1-0562-49b6-8412-74cdddeb0afc"
        ),
        ArchitectureClass(
            "libagora_content_inspect_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86%2Flibagora_content_inspect_extension.so?alt=media&token=3b479036-a1fd-4efd-8911-f24a184a281f"
        ),
        ArchitectureClass(
            "libagora_dav1d", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot.com/o/AGORA%20FILES%2Fx86%2Flibagora_dav1d" +
                    ".so?alt=media&token=3703121e-8a39-4640-bce4-677d26840b52"
        ),
        ArchitectureClass(
            "libagora_drm_loader_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86%2Flibagora_drm_loader_extension" +
                    ".so?alt=media&token=502dac24-2800-48d9-b1be-bdcab49b4f56"
        ),
        ArchitectureClass(
            "libagora_screen_capture_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86%2Flibagora_screen_capture_extension" +
                    ".so?alt=media&token=d379dccd-83d3-4a86-817e-b300b5a46734"
        ),
        ArchitectureClass(
            "libagora_segmentation_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86%2Flibagora_segmentation_extension" +
                    ".so?alt=media&token=3c33bb0d-df4a-4902-993f-8624f7af0b9a"
        ),
        ArchitectureClass(
            "libagora_spatial_audio_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86%2Flibagora_spatial_audio_extension" +
                    ".so?alt=media&token=84a7e89a-09c4-4999-bf03-910d0ce3d829"
        ),
        ArchitectureClass(
            "libagora_super_resolution_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86%2Flibagora_super_resolution_extension" +
                    ".so?alt=media&token=eb3ef571-2cae-486a-96d0-d9fde2146d40"
        ),
        ArchitectureClass(
            "libagora_udrm3_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86%2Flibagora_udrm3_extension" +
                    ".so?alt=media&token=cc4b6744-f70a-4668-83b7-3fa2d8e6e23e"
        ),
        ArchitectureClass(
            "libagora_video_quality_analyzer_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86%2Flibagora_video_quality_analyzer_extension" +
                    ".so?alt=media&token=9748090b-fdd2-4310-8b6c-68231ad79054"
        ),
    )

    private val x86_64 = arrayListOf(
        ArchitectureClass(
            "libagora-fdkaac", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot.com/o/AGORA%20FILES%2Fx86_64%2Flibagora-fdkaac" +
                    ".so?alt=media&token=e63740dd-b5a5-49e0-a35c-7d0cc058ceea"
        ),
        ArchitectureClass(
            "libagora-ffmpeg", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora-ffmpeg.so?alt=media&token=ad31e838-a49b-4256-85e3-26d32a0a3a2f"
        ),
        ArchitectureClass(
            "ibagora-rtc-sdk", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora-rtc-sdk.so?alt=media&token=491935bf-723f-41db-ac06-4c1a7fbfab36"
        ),
        ArchitectureClass(
            "libagora-soundtouch", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora-soundtouch.so?alt=media&token=d8dd93f3-e385-4d85-8ac9-85c1ca1a73ba"
        ),
        ArchitectureClass(
            "libagora_ai_echo_cancellation_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora_ai_echo_cancellation_extension.so?alt=media&token=4737c084-0391-4486-b46a-e27e90776860"
        ),
        ArchitectureClass(
            "libagora_ai_noise_suppression_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora_ai_noise_suppression_extension.so?alt=media&token=6e6e1517-9320-4aca-acb6-aa6e93018948"
        ),
        ArchitectureClass(
            "libagora_audio_beauty_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora_audio_beauty_extension.so?alt=media&token=c6ed3c83-d97b-472e-a9e8-d9311213b0ec"
        ),
        ArchitectureClass(
            "libagora_clear_vision_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora_clear_vision_extension.so?alt=media&token=efdd0a1c-0794-4a90-8d99-aabc082209b8"
        ),
        ArchitectureClass(
            "libagora_content_inspect_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora_content_inspect_extension.so?alt=media&token=64381858-bfa1-423a-b9b9-f0e4c9a9087e"
        ),
        ArchitectureClass(
            "libagora_dav1d", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot.com/o/AGORA%20FILES%2Fx86_64%2Flibagora_dav1d" +
                    ".so?alt=media&token=b80a4f48-e1a8-48a2-9288-6a4e0712929c"
        ),
        ArchitectureClass(
            "libagora_drm_loader_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora_drm_loader_extension.so?alt=media&token=8d8b9569-c268-4c9d-a4b0-6a0a3fae3c26"
        ),
        ArchitectureClass(
            "libagora_screen_capture_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora_screen_capture_extension" +
                    ".so?alt=media&token=138f3fe8-ef97-4764-b8eb-25bb7f737601"
        ),
        ArchitectureClass(
            "libagora_segmentation_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora_segmentation_extension" +
                    ".so?alt=media&token=8f112e96-53b1-41c2-b7d9-5c8a2be6d790"
        ),
        ArchitectureClass(
            "libagora_spatial_audio_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora_spatial_audio_extension" +
                    ".so?alt=media&token=0398e391-ee65-4980-81ab-5586619b0a14"
        ),
        ArchitectureClass(
            "libagora_super_resolution_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora_super_resolution_extension" +
                    ".so?alt=media&token=3c798e9d-70ff-4c41-a4dd-94991d2e8657"
        ),
        ArchitectureClass(
            "libagora_udrm3_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora_udrm3_extension" +
                    ".so?alt=media&token=238331b1-54f9-47d0-b1b9-ea4ed67744ac"
        ),
        ArchitectureClass(
            "libagora_video_quality_analyzer_extension", "https://firebasestorage.googleapis.com/v0/b/howfar-b24ef.appspot" +
                    ".com/o/AGORA%20FILES%2Fx86_64%2Flibagora_video_quality_analyzer_extension" +
                    ".so?alt=media&token=b8abb1e9-177c-4e6e-a7ba-f0fb3ff44a7b"
        ),
    )
}

data class ArchitectureClass(
    var fileName: String = "",
    var downloadLink: String = ""
)