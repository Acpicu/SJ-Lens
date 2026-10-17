# R8 混淆规则。
# 本项目没有反射调用的模型（JSON 手工解析），所以不需要额外 keep 规则。
# 若以后接入需要反射的库（如 Room、kotlinx.serialization），在此补对应规则。