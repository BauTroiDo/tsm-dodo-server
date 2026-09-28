CollectWarriorData = {}
CollectWarriorData.__index = CollectWarriorData;

function CollectWarriorData.New(reader)
  local self = {};
  setmetatable(self, CollectWarriorData);
  self.id = reader:ReadUInt16();   
  self.getFlag = reader:ReadUInt16();
  self.flag = reader:ReadUInt16(); 
  self.gainStr = reader:ReadUInt32();

  self.award = {};
  for i = 1, 5 do 
    self.award[i] = {};
    self.award[i].itemId = reader:ReadUInt16(); 
    self.award[i].count = reader:ReadUInt16(); 
  end

  self.storyStr = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16())); --�ͥ� Length <= 254
  return self;
end