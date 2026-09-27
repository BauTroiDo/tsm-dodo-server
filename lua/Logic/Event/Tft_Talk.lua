Tft_Talk = {};
Tft_Talk.__index = Tft_Talk;

function Tft_Talk.New(reader)
  local self = {};
  setmetatable(self, Tft_Talk);
  
  self.npcId = reader:ReadInt32(); --對話角色編號
  self.talkId = reader:ReadInt32(); --對話內容編號
  self.point = reader:ReadInt32(); --對話播放時間點
  self.duration = reader:ReadInt32(); --對話播放持續時間

  self.overTime = 0; --對話結束時間
  self.isOver = false; --對話是否播完

  return self;
end

function Tft_Talk:Destroy()
end

function Tft_Talk:Show(roleController, timeScale)
  logError(string.Concat("Movie ", roleController.name, " Talk ", talkDatas[self.talkId]));
  ShowTalkMessage(nil, talkDatas[self.talkId], roleController);
  
  self.overTime = CGTimer.time + (self.duration / timeScale);
  self.isOver = false;
end

function Tft_Talk:IsOverTime()
  if CGTimer.time > self.overTime then
    self.overTime = 0;
    self.isOver = true;

    return true;
  end
  
  return false;
end
