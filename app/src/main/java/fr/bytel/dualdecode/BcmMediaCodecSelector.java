package fr.bytel.dualdecode;

import android.util.Log;


import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;

import java.util.List;
import java.util.stream.Collectors;


// Custom MediaCodecSelector implementations to force using specific Broadcom video decoders
public interface BcmMediaCodecSelector extends MediaCodecSelector {
    String LOG_TAG = "BcmMediaCodecUtil";
    String broadcom_r = "bcm";
    String broadcom_p = "broadcom";
    String tunnel = "tunnel";
    String redux = "redux";

    static void logList(List<MediaCodecInfo> decoderInfoList, List<MediaCodecInfo> newList) {
        for (MediaCodecInfo decoderInfo : decoderInfoList) {
            boolean selected = newList.contains(decoderInfo);
            Log.d(LOG_TAG, "decoderInfo.name: " + decoderInfo.name+
                    ", hwAcc="+decoderInfo.hardwareAccelerated+
                    ", softw="+decoderInfo.softwareOnly+
                    ", tunnel="+decoderInfo.tunneling+
                    ", adapt="+decoderInfo.adaptive+
                    ", secure="+decoderInfo.secure+
                    ", maxCount="+decoderInfo.getMaxSupportedInstances()+
                    ", selected="+selected);
        }
    }

    MediaCodecSelector BCM_VIDEO =
            (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
                List<MediaCodecInfo> decoderInfoList = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, false);
                if (mimeType.startsWith("video/")) {
                    Log.d(LOG_TAG, "BCM_VIDEO");
                    List<MediaCodecInfo> newList = decoderInfoList.stream()
                            .filter(p -> ((p.name.contains(broadcom_p) ||
                                           p.name.contains(broadcom_r))  &&
                                        !p.name.contains(redux) &&
                                        !p.name.contains(tunnel)))
                            .collect(Collectors.toList());
                    logList(decoderInfoList, newList);
                    return newList;
                }
                return decoderInfoList;
            };

    MediaCodecSelector BCM_VIDEO_REDUX =
            (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
                List<MediaCodecInfo> decoderInfoList = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
                if (mimeType.startsWith("video/")) {
                    Log.d(LOG_TAG, "BCM_VIDEO_REDUX");
                    List<MediaCodecInfo> newList = decoderInfoList.stream()
                            .filter(p -> p.name.contains(redux))
                            .collect(Collectors.toList());
                    logList(decoderInfoList, newList);
                    return newList;
                }
                return decoderInfoList;
            };
}
