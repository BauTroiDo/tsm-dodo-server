ESurvive = {
  None = 0,
  Maze = 1,
  Fight = 2,
}

Survive = {}
local this = Survive;

this.kind = ESurvive.None;
this.lifeCount = 0; --活人數

this.killCount = 0; --殺敵數(原LifeKillEmyNum)
this.dieCount = 0; --死亡數(原LifeDeathNum)

function Survive.GetSurviveKind(sceneId)
  if Between(sceneId, 54551, 54554) or Between(sceneId, 54556, 54559) then
    return ESurvive.Maze;
  elseif Between(sceneId, 54541, 54549) then
    return ESurvive.Fight;
  else
    return ESurvive.None;
  end
end