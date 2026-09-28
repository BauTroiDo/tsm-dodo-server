--region MovieSound.lua


local SoundList = 
{
  "Sound\\10.wav",
  "Sound\\20.wav",
  "Sound\\21.wav",
  "Sound\\30.wav",
  "Sound\\31.wav",
  "Sound\\32.wav",
  "Sound\\40.wav",
  "Sound\\41.wav",
  "Sound\\42.wav",
  "Sound\\43.wav",
  "Sound\\50.wav",
  "Sound\\51.wav",
  "Sound\\52.wav",
  "Sound\\m004.wav",
  "Sound\\WA0001.wav",
  "Sound\\WA0002.wav",
  "Sound\\WA0003.wav",
  "Sound\\WA0004.wav",
  "Sound\\WA0005.wav",
  "Sound\\WA0006.wav",
  "Sound\\WA0007.wav",
  "Sound\\WA0008.wav",
  "Sound\\WA0009.wav",
  "Sound\\WA0010.wav",
  "Sound\\WA0011.wav",
  "Sound\\WA0012.wav",
  "Sound\\WA0013.wav",
  "Sound\\WA0014.wav",
  "Sound\\WA0017.wav",
  "Sound\\WA0018.wav",
  "Sound\\WA0019.wav",
  "Sound\\WA0020.wav",
  "Sound\\WA0021.wav",
  "Sound\\WA0022.wav",
  "Sound\\WA0023.wav",
  "Sound\\WA0024.wav",
  "Sound\\WA0025.wav",
  "Sound\\WA0026.wav",
  "Sound\\WA0027.wav",
  "Sound\\WA0028.wav",
  "Sound\\WA0029.wav",
  "Sound\\WA0030.wav",
  "Sound\\WA0031.wav",
  "Sound\\WA0032.wav",
  "Sound\\WA0033.wav",
  "Sound\\WA0034.wav",
  "Sound\\WA0035.wav",
  "Sound\\WA0036.wav",
  "Sound\\WA0037.wav",
  "Sound\\WA0038.wav",
  "Sound\\WA0040.wav",
  "Sound\\WA0041.wav",
  "Sound\\WA0042.wav",
  "Sound\\WA0043.wav",
  "Sound\\WA0044.wav",
  "Sound\\WA0045.wav",
  "Sound\\WA0046.wav",
  "Sound\\WA0047.wav",
  "Sound\\WA0048.wav",
  "Sound\\WA0049.wav",
  "Sound\\WA0051.wav",
  "Sound\\WA0052.wav",
  "Sound\\WA0053.wav",
  "Sound\\WA0054.wav",
  "Sound\\WA0055.wav",
  "Sound\\WA0056.wav",
  "Sound\\WA0057.wav",
  "Sound\\WA0058.wav",
  "Sound\\WA0059.wav",
  "Sound\\WA0060.wav",
  "Sound\\WA0061.wav",
  "Sound\\WA0062.wav",
  "Sound\\WA0063.wav",
  "Sound\\WA0064.wav",                     
  --資料片二
  "Sound\\WA0065.wav",--龍吼
  "Sound\\WA0066.wav",--擊地
  "Sound\\WA0067.wav",--鳳叫
  "Sound\\WA0068.wav",--拍翅
  "Sound\\WA0069.wav",--集氣
  "Sound\\WB0002.wav",
  "Sound\\WB0003.wav",
  "Sound\\WB0004.wav",
  "Sound\\WB0005.wav",
  "Sound\\WB0006.wav",
  "Sound\\WB0007.wav",
  "Sound\\WB0008.wav",
  "Sound\\WB0009.wav",
  "Sound\\WB0010.wav",
  "Sound\\WB0011.wav",
  "Sound\\WB0012.wav",
  "Sound\\WB0013.wav",
  "Sound\\WB0014.wav",
  "Sound\\WB0015.wav",
  "Sound\\WB0016.wav",
  "Sound\\WB0017.wav",
  "Sound\\WB0018.wav",
  "Sound\\WB0019.wav",
  "Sound\\WB0020.wav",
  "Sound\\WC0001.wav",
  "Sound\\WC0002.wav",
  "Sound\\WC0003.wav",
  "Sound\\WC0004.wav",
  "Sound\\WC0005.wav",
  "Sound\\WC0006.wav",
  "Sound\\WC0007.wav",
  "Sound\\WC0008.wav",
  "Sound\\WC0009.wav",
  "Sound\\WC0010.wav",
  "Sound\\WC0012.wav",  --109
  "Sound\\Wd0001.wav",  --110
  "Sound\\Wd0002.wav",  --111
  "Mp3s\\WM0001.mp3",   --112
  "Mp3s\\WM0002.mp3",   --113
  "Mp3s\\WM0003.mp3",   --114
  "Mp3s\\WM0004.mp3",   --115
  "Mp3s\\WM0005.mp3",   --116
  "Mp3s\\WM0006.mp3",   --117
  "Mp3s\\WM0007.mp3",   --118
  --2004/11/29
  "Mp3s\\WM0008.mp3",   --119
  "Mp3s\\WM0009.mp3",   --120
  "Mp3s\\WM00010.mp3",  --121
  "Mp3s\\WM00011.mp3",  --122
  "Sound\\WA0070.wav",  --123
  "Sound\\WA0071.wav",  --124
  "Sound\\WA0072.wav",  --125
  "Sound\\WA0073.wav",  --126
  "Sound\\WA0074.wav",  --127
  "Sound\\WA0075.wav",  --128
  "Sound\\WC0013.wav",  --129
  "Sound\\WC0014.wav",  --130
  "Sound\\WC0015.wav",  --131
  "Sound\\WC0016.wav"   
}

MovieSound = {};
local this = MovieSound;

function MovieSound.PlayMovieSound(soundID)
  local path = SoundList[soundID];
  if path ~= nil then
    AudioManager.PlaySlot(path);
  end
end
--endregion
