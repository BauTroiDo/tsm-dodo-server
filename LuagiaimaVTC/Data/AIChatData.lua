AIChatData = {};
AIChatData.__index = AIChatData;

function AIChatData.New(reader)
  local self = {};
  setmetatable(self, AIChatData);
  
  self.no  = reader:ReadUInt16();
  self.talkId = reader:ReadUInt32();
  self.talkName = reader:ReadUInt32();
  self.icon = reader:ReadUInt32();
  self.state = reader:ReadUInt32();
  self.openDay = reader:ReadByte();
  self.talkKind = reader:ReadByte();
  self.elfId = reader:ReadUInt16();
  self.haveToTalk = reader:ReadByte();
  self.used = false;
  
  return self.no, self;
end