local EMissionAwardResult =
{
  unknown = 0,
  success = 1,
  qualify = 2,
  received = 3
}

MissionAwardData = {}
MissionAwardData.__index = MissionAwardData;

function MissionAwardData.New(reader)
  local self = {};
  setmetatable(self, MissionAwardData);

  self.Id = reader:ReadUInt16();--任務ID
  self.contentID = reader:ReadUInt32();--達成字串

  self.conditions = {};
  self.conditions.kind = reader:ReadByte();--任務類別
  self.conditions.kindValue = reader:ReadUInt32();--類別數值
  self.conditions.opr =  reader:ReadByte();--運算子
  self.conditions.value = reader:ReadUInt32();--任務達成數值

  self.itemId = reader:ReadUInt16();--獎勵物品ID
  self.itemCount = reader:ReadUInt32();--獎勵物品數量
  self.getFlag = reader:ReadUInt16();--領獎永標
  
  self.activityId = reader:ReadUInt16();--活動ID
  self.stringType = reader:ReadByte();--字串類型
  self.descriptionItemId = reader:ReadUInt32();--任務內容物品ID
  
  return self;
end

function MissionAwardData:GetName()
  return string.Get(self.name);
end

function MissionAwardData:GetContent()
  return self.content
end

function MissionAwardData:GetSchedule()
  return string.Concat(self:GetConditionCurrentValue(), " / ", self.conditions.value);
end

function MissionAwardData:IsComplete()
  if self.conditions.opr == 1 then
    return self:GetConditionCurrentValue() == self.conditions.value;
  elseif self.conditions.opr == 2 then
    return self:GetConditionCurrentValue() > self.conditions.value;
  elseif self.conditions.opr == 3 then
    return self:GetConditionCurrentValue() >= self.conditions.value;
  elseif self.conditions.opr == 4 then
    return self:GetConditionCurrentValue() < self.conditions.value;
  elseif self.conditions.opr == 5 then
    return self:GetConditionCurrentValue() <= self.conditions.value;
  elseif self.conditions.opr == 6 then
    return self:GetConditionCurrentValue() ~= self.conditions.value;
  end

  return false;
end

function MissionAwardData:HaveGetFlag()
  return BitFlag.Get(self.getFlag);
end

function MissionAwardData:GetConditionCurrentValue()
  if CheckCondition.CalculationConditionValue[self.conditions.kind] == nil then
    return 0;
  end
  return CheckCondition.CalculationConditionValue[self.conditions.kind](self);
end
