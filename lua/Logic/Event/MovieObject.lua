--region MovieObject.lua
--[[
     功能:演出物件
 ]]
require "Logic/Event/Tft_Crunode"
require "Logic/Event/Tft_Efficacy"
require "Logic/Event/Tft_Ground"
require "Logic/Event/Tft_Picture"
require "Logic/Event/Tft_Role"
require "Logic/Event/Tft_Talk"

MovieObject = {};
MovieObject.__index = MovieObject;

function MovieObject.New(reader)
  local self = {};
  setmetatable(self, MovieObject);

  self.version = reader:ReadByte(); --儲存動畫檔版本
  local playerCount = reader:ReadInt32(); --儲存玩家數量
  local npcCount = reader:ReadInt32(); --儲存NPC數量
  local pictureCount = reader:ReadInt32(); --儲存動畫圖片數量
  local groundCount = reader:ReadInt32(); --儲存背景圖數量
  local talkCount = reader:ReadInt32(); --儲存對話數量
  self.totalPoint = reader:ReadInt32(); --儲存總設置時點數量    

  self.pactor = nil;
  if playerCount > 0 then
    self.pactor = Tft_Role.New(reader);
  end
  
  self.nactor = {};
  for i = 1, npcCount do
    self.nactor[i] = Tft_Role.New(reader);
  end
  
  self.pic = {};
  for i = 1, pictureCount do
    self.pic[i] = Tft_Picture.New(reader);
  end

  self.ground = nil;
  if groundCount > 0 then
    self.ground = Tft_Ground.New(reader);
  end
  
  if self.totalPoint > 0 then
    self.crunNode = Tft_Crunode.New();
    self.crunNode:Init(reader, self.totalPoint);    
     
    self.efficacy = Tft_Efficacy.New();
    self.efficacy:Init(reader, self.totalPoint);    
  end

  self.talk = {};
  for i = 0, talkCount do
    self.talk[i] = Tft_Talk.New(reader);
  end

  return self;
end
--endregion
