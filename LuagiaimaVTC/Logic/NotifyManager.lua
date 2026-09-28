ENotifyManager = {
  Loading = "Loading",
  FinishLoading = "FinishLoading",
  EnterScene = "EnterScene",
  ExitScene = "ExitScene",
  RoleCount = "RoleCount",
  CheckCondition = "CheckCondition"
};

NotifyManager = {};
local this = NotifyManager;

this.NotifyTB = {};

function NotifyManager.Register(subjectName, id, notifyFunc)
  if this.NotifyTB[subjectName] ~= nil and this.NotifyTB[subjectName][id] ~= nil then  
    return;
  end
  
  log(string.Concat("subjectName: ", subjectName));
  
  if this.NotifyTB[subjectName] == nil then
    this.NotifyTB[subjectName] = {};
  end
  
  this.NotifyTB[subjectName][id] = notifyFunc;
end

function NotifyManager.UnRegister(subjectName, id)
  if this.NotifyTB[subjectName] ~= nil then
    this.NotifyTB[subjectName][id] = nil;
  end
end

function NotifyManager.Notify(subjectName, data)	
--  log(string.Concat("Notify Subject: ", subjectName));
  
  if this.NotifyTB[subjectName] ~= nil then
    for k, callbackFunc in pairs(this.NotifyTB[subjectName]) do
      callbackFunc(data);
    end
  end
end