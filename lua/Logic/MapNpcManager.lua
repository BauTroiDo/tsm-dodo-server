MapNpcManager = {};
local this = MapNpcManager;

local mapNpcDatas = {};

function MapNpcManager.Initialize()
  NotifyManager.Register(ENotifyManager.EnterScene, "MapNpcManager", this.OnEnterScene);
  NotifyManager.Register(ENotifyManager.ExitScene, "MapNpcManager", this.OnExitScene);
end

function MapNpcManager.OnEnterScene(checkCode)
  mapNpcDatas = SceneManager.sceneState.mapNpcTable;

  if mapNpcDatas == nil then
    logError("MapNpcManager.OnEnterScene mapNpcDatas = nil");
  end

  --記錄npc有可能出現的動作
  local moveNpcs = {};
  local actionNpcs = {};
  local npcEvents = SceneManager.sceneState.npcEventTable;
 
  if npcEvents ~= nil then
    for k, v in pairs(npcEvents) do
      for i = 1, v.ConditionArrayNum do
        for j = 1, v.ConditionArray[i].ResultArrayNum do
          local data = v.ConditionArray[i].ResultArray[j];
          if data.ResultClass == 3 then
            if data.ResultType == 4 then
              --move
              moveNpcs[data.Parameter] = true;
            end
            
            if data.ResultType == 9 and data.ParamterStyle == 2 then
              --action
              if actionNpcs[data.Parameter] == nil then
                actionNpcs[data.Parameter] = {};
              end
  
              table.insert(actionNpcs[data.Parameter], data.ResultValue);
            end
          end
        end
      end	
    end
  end
  
  if mapNpcDatas ~= nil then
    for k, v in pairs(mapNpcDatas) do
      if not v.close then
        local actions = {};

        if v.motionType ~= 4 then
          table.insert(actions, ERolePose.Stand);
        end

        if v.motionType ~= 1 or v.traceRadius > 0 or moveNpcs[v.id] ~= nil then
          table.insert(actions, ERolePose.Walk);
        end

        if v.roleStatus.isLie then
          table.insert(actions, ERolePose.Lie);
        end

        if actionNpcs[v.id] ~= nil then
          for kk, vv in pairs(actionNpcs[v.id]) do
            table.insert(actions, vv);
          end
        end

        Role.MapNpcAppear(v.id, v, actions);
      end
    end
  end
end

function MapNpcManager.OnExitScene()
  if mapNpcDatas ~= nil then
    for k, v in pairs(mapNpcDatas) do
      if not v.close then
        Role.MapNpcDisappear(v.id, v);
      end
    end
  end

  mapNpcDatas = nil;
end