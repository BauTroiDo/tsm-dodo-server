ActivityConditionData = {}
ActivityConditionData.__index = ActivityConditionData;

function ActivityConditionData.New(reader)
  local self = {};
  setmetatable(self, ActivityConditionData);
  
  self.typeId = reader:ReadByte();     --類型ID
  self.stringId = reader:ReadUInt32();   --使用字串

  return self.typeId, self;
end
