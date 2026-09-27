FightWinLoseCondition = {}
FightWinLoseCondition.__index = FightWinLoseCondition;

local this = FightWinLoseCondition;
ConditionTemp = {} --勝敗條件暫存
function ConditionTemp.New(data)
  local self = {};
  self.conditionClass = data.logic.ConditionClass;       --勝敗條件
  self.conditionValue = data.logic.ConditionValue;       --勝敗條件數值
  self.conditionOps = data.WinConditionOps;         --勝敗條件運算子
  self.conditionParameter = data.Parameter;   --特定id
  return self;
end

local winCondition = {}; --array [0~WinConditionMaxRange] of ConditionTemp;//勝利條件暫存
local loseCondition = {}; --array [0~WinConditionMaxRange] of ConditionTemp;//勝利條件暫存

function FightWinLoseCondition.GetCondition()  
  local winConditionNum, loseConditionNum;
  local data = SceneManager.sceneState.fightTable[FightField.fightNo];
  if data == nil then 
    logError(string.Concat("SceneManager.sceneState.fightTable[", FightField.fightNo, "] is Nil"));
    return;
  end

  winConditionNum = data.FightEventAy[1].WinConditionArrayNum;
  loseConditionNum = data.FightEventAy[2].WinConditionArrayNum;
  table.Clear(winCondition);
  table.Clear(loseCondition);

  for i=1, winConditionNum do  
    winCondition[i] = ConditionTemp.New(data.FightEventAy[1].WinConditionAy[i])
  end

  for i=1, loseConditionNum do
    loseCondition[i] = ConditionTemp.New(data.FightEventAy[2].WinConditionAy[i]);
  end
end

function FightWinLoseCondition.GetWinLoseStr(condition, str)
  local data = SceneManager.sceneState.fightTable[FightField.fightNo];

  if condition == 1 then  --取得勝利條件字串    
    for i = 1, data.FightEventAy[condition].WinConditionArrayNum do      
      if winCondition[i].conditionClass == 1 then 
      elseif winCondition[i].conditionClass == 3 then 
        str = string.Concat(str, ", ", string.Get(90022), winCondition[i].conditionValue, " ", string.Get(90023));
      end
    end      
  elseif condition == 2 then  --取得敗戰條件字串    
    for i = 1, data.FightEventAy[condition].WinConditionArrayNum do      
      if loseCondition[i].conditionClass == 1 then           
      elseif loseCondition[i].conditionClass == 4 then 
        str = string.Concat(str, ", ", string.Get(90026), loseCondition[i].conditionValue, " ", string.Get(90023));
      elseif loseCondition[i].conditionClass == 8 then           
        local npcdata = npcDatas[loseCondition[i].conditionParameter];
        if npcdata ~= nil then
          str = table.concat(str, ", ", string.Get(90024), " ", npcdata.name, " ", string.Get(90025));          
        end
      end    
    end
  end
  return str;
end

function FightWinLoseCondition.ShowWinLoseList() --顯示勝敗條件
  local winStr, loseStr;
  
  winStr = string.Concat(string.Get(90018), "\n", string.Get(90020)); --string.Get(90018) = '勝利條件：' string.Get(90020) = '敵方全滅'
  loseStr = string.Concat("\n", "\n", string.Get(90019), "\n", string.Get(90021)); --string.Get(90019) = '敗戰條件：'string.Get(90021) = '我方全滅'

  if FightField.fightNo ~= 0 then --如果非事件產生之戰鬥，則秀"敵方全滅、我方全滅"
    this.GetCondition(); --取得勝敗條件
    --取得勝利、敗戰條件字串
    winStr = this.GetWinLoseStr(1,winStr);
    loseStr = this.GetWinLoseStr(2,loseStr);
  end
  
  ShowWinLoseCondition(function() UI.Close(UIConditionOfWinOrLose); end, string.Concat(winStr, loseStr));
  --WF_TalkMsgFormPlus.showMsg(WinStr+LoseStr,5000,true); //秀勝敗條件
end