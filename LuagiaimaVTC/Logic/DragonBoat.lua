DragonBoat = {};
local this = DragonBoat;

this.inFestival = false;

function DragonBoat.Initialize()  
end

function DragonBoat.InRaceScene()
  return Between(SceneManager.sceneId, 49911, 49915) or Between(SceneManager.sceneId, 49963, 49982);
end

function DragonBoat.ShowNextMessage()
  local message = nil;
  
  message = string.Get(80236);

  if string.IsNullOrEmpty(message) then return end

  ShowTalkMessage(nil, message, Role.mapNpcs[1]);
end

function DragonBoat.ActivityStart()
  this.inFestival = true;
end

function DragonBoat.ActivityEnd()
  this.inFestival = false;
end