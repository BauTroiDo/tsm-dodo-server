--region Tft_Efficacy.lua  紀錄各節點特效處理資訊
Tft_Efficacy = {};
Tft_Efficacy.__index = Tft_Efficacy;

function Tft_Efficacy.New()
  local self = {};
  setmetatable(self, Tft_Efficacy);

  self.Eff1 = {};                --0: 畫面不調整明暗  1:畫面由暗到亮  2:畫面由亮到暗
  self.Eff2 = {};                --0: 無              1:地震效果一    2:地震效果二
  self.Eff3 = {};                --0: 無              1:地震效果一    2:地震效果二
  self.Eff4 = {};                --0: 無   其餘：背景音樂檔編號 92/12/23
  self.OverTime1 = 0;
  self.OverTime2 = 0;
  self.OverTime3 = 0;
  self.OverTime4 = 0;

  return self;
end

function Tft_Efficacy:Init(reader, totalPoint)
  for i = 0, totalPoint do
    self.Eff1[i] = reader:ReadByte();
    self.Eff2[i] = reader:ReadByte();
    self.Eff3[i] = reader:ReadByte();
    self.Eff4[i] = reader:ReadByte();              
  end
end

function Tft_Efficacy:Destroy()
end

function Tft_Efficacy:IsOverTime(DTime, aKind)    
  if aKind == 1 then
    if math.round((CGTimer.time - self.OverTime1) * 1000) > DTime then
      self.OverTime1 = CGTimer.time;
      return true;
    end
  elseif aKind == 2 then
    if math.round((CGTimer.time - self.OverTime2) * 1000) > DTime then
      self.OverTime2 = CGTimer.time;
      return true;
    end
  elseif aKind == 3 then
    if math.round((CGTimer.time - self.OverTime3) * 1000) > DTime then
      self.OverTime3 = CGTimer.time;
      return true;
    end
  elseif aKind == 4 then
    if math.round((CGTimer.time - self.OverTime4) * 1000) > DTime then
      self.OverTime4 = CGTimer.time;
      return true;
    end
  end

  return false;
end
