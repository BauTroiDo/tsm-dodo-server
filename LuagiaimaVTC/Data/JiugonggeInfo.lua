JiugonggeInfo = {}
JiugonggeInfo.__index = JiugonggeInfo;

local this = JiugonggeInfo;

local length = 0;

function JiugonggeInfo.New(reader)
  local self = {};
  setmetatable(self, JiugonggeInfo);
  
  self.Id = reader:ReadUInt16();--流水號
  self.activityId = reader:ReadUInt16();--活動ID
  self.kind = reader:ReadByte();--種類
  self.kindName = reader:ReadUInt32()--種類名稱
  self.reset = reader:ReadByte();--重置
  self.awards = {};

  for i = 1, 7 do
    self.awards[i] = {}
    self.awards[i].Id = reader:ReadUInt16();--獎
    self.awards[i].quant = reader:ReadUInt32();--數量
    self.awards[i].getFlag = reader:ReadUInt16();--領獎標記
  end

  return self;
end
