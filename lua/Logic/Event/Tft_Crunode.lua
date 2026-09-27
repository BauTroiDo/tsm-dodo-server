Tft_Crunode = {};
Tft_Crunode.__index = Tft_Crunode;

function Tft_Crunode.New()
  local self = {};
  setmetatable(self, Tft_Crunode);

  self.time = {};
  self.overTime = 0;

  return self;
end

function Tft_Crunode:Init(reader, totalPoint)  
  for i = 0, totalPoint do
    self.time[i] = reader:ReadInt32();    
  end

  self.overTime = CGTimer.time;
end

function Tft_Crunode:Destroy()
end

function Tft_Crunode:IsOverTime(DTime)
  local timeSpan = (CGTimer.time - self.overTime) * 1000;
  if math.round((CGTimer.time - self.overTime) * 1000) > DTime then
    --TlogError("Tft_Crunode:IsOverTime", string.format("Time Span: %s, DTime: %s", tostring(timeSpan), tostring(DTime)));
    self.overTime = CGTimer.time;
    return true;
  end

  return false;
end