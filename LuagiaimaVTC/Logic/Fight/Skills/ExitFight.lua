ExitFight = InheritsFromBaseAttackSkill();

function ExitFight:InitExtraParameters()
  FightField.fightHum[self.humIdx].blnExiting = true;
  self.aEffCount = 1;
end

function ExitFight:Free()
  local me = FightField.fightHum[self.humIdx];
  if me ~= nil then 
    me:SetStatus(EFightStatusKind.Seal, ESkill_Kind.Dis);   --還原封印
    me:SetStatus(EFightStatusKind.Vary, ESkill_Kind.Dis);   --還原變化
    me:SetStatus(EFightStatusKind.Faint, ESkill_Kind.Dis);  --還原衰弱
    me:SetStatus(EFightStatusKind.Spec, ESkill_Kind.Dis);   --還原特殊
    me:ExitFight();
  end

  FightField.RestoreFightInfo(self.humIdx, self.aEffCount);
end

function ExitFight:Update()
  local me = FightField.fightHum[self.humIdx];
  if me == nil then 
    logError(string.Concat("Exitfight me nil ", self.humIdx));
    self.isLightEnd = true;
    return;
  end
  if self.step == 1 then
    local newPos = me:GetSpotOfRc(EFightSpot.Back, 60);
    
    me.roleController:SetInBattle(false);
    me.roleController:SetIsMoving(true);
    me:SetSpeed(0.12);
    me:SetWalk(newPos.x, newPos.y);
    self.step = self.step + 1;
  elseif self.step == 2 then
    if me.arrive then
      self.step = self.step + 1;
      self.isLightEnd = true;
    end
  end
end