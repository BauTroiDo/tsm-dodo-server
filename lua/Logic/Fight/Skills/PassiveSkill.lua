--狀態顯示
PassiveSkill = InheritsFromBaseStatus();

function PassiveSkill:Update()  
  if self.step == 1 then    
    local me = FightField.fightHum[self.humIdx];
    local lightId = 0;
    local skillKind = self.showKind;
    local effectPosY = 0;
    local startPage = 1;    --預設被動光影張數為1-12張
    local endPage = 12;
    if skillKind == ESkill_Kind.EW_Int then
      lightId = 10995;
    elseif skillKind == ESkill_Kind.EW_Atk then
      lightId = 10996;
    elseif Contains(skillKind,ESkill_Kind.EW_Def, ESkill_Kind.EW_BaguaDex) then
      lightId = 10997;
    elseif skillKind == ESkill_Kind.EW_Hpx then
      lightId = 10998;
    elseif skillKind == ESkill_Kind.EW_Spx then
      lightId = 10999;
    elseif skillKind == ESkill_Kind.EW_Dex then
      lightId = 11000;
    elseif skillKind == ESkill_Kind.EW_StrikeRate then
      lightId = 11001;
    elseif skillKind == ESkill_Kind.EW_Earth then
      lightId = 11002;
    elseif skillKind == ESkill_Kind.EW_Water then
      lightId = 11003;
    elseif skillKind == ESkill_Kind.EW_Fire then
      lightId = 11004;
    elseif skillKind == ESkill_Kind.EW_Wind then
      lightId = 11005;
    elseif Contains(skillKind, ESkill_Kind.EW_MirrorIngore, ESkill_Kind.EW_SpecifyStatusIngore) then
      lightId = 12051;
    elseif skillKind == ESkill_Kind.DodgeDead then 
      lightId = 20001;
    elseif skillKind == ESkill_Kind.EW_Counter then 
      lightId = 20014;
    elseif skillKind == ESkill_Kind.EW_CounterTrance then
      lightId = 20055;
    elseif skillKind == ESkill_Kind.EW_DevourMeatDisStatis then
      lightId = 10952;
      effectPosY = 20;
    elseif skillKind == ESkill_Kind.EW_DevourMeatCounter then
      lightId = 10976;
    elseif skillKind == ESkill_Kind.AresComeMe then
      endPage = 8;
      lightId = 10791;
    end

    me:SetStatusKind(EFightStatusKind.Passive, skillKind);
    me:GetStatus(EFightStatusKind.Passive):SetLightWithOrder(1, EEffectLightDis.Body, lightId, 100, startPage, endPage, endPage, EEffectLightTracer.Stand, me.nowPos.x, me.nowPos.y + effectPosY, 30, true, 30);
    me:GetStatus(EFightStatusKind.Passive).lights[1]:SetSize(128, 128);
    self.step = 2;    
  end
end

CommonStatus = InheritsFromBaseStatus();

function CommonStatus:ExtraClose()
  local me = FightField.fightHum[self.humIdx];
  if me ~= nil then 
    me.roleController:SetStatusShow(self.statusKind, false);
  end
end

function CommonStatus:Update()  
  if self.step == 1 then    
    local me = FightField.fightHum[self.humIdx];
    if me ~= nil then       
      me.roleController:SetStatusShow(self.statusKind, true);
      local status =  me:GetStatus(self.statusKind);
      if self.showKind == ESkill_Kind.EWCommonFaint then 
        status:SetLightWithOrder(1, EEffectLightDis.Body, 11007, 120, 1, 12, 12, EEffectLightTracer.StandAni, me.nowPos.x, me.nowPos.y + 25, 255, false);
        status.lights[1]:SetSize(128,128);
      else
        status:SetLightWithOrder(1, EEffectLightDis.Body, 11006, 90, 1, 8, 8, EEffectLightTracer.StandAni, me.nowPos.x, me.nowPos.y + 25, 30, false);
        status.lights[1]:SetSize(128,128);
      end
    end
    self.step = 2;    
  end
end