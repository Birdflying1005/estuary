package com.neighborhood.aka.laplce.estuary.bean.datasink
import com.neighborhood.aka.laplce.estuary.bean.datasink.DataSinkType.DataSinkType

/**
  * Created by john_liu on 2018/2/7.
  */
trait KafkaBean extends DataSinkBean{
 override var dataSinkType: DataSinkType = DataSinkType.KAFKA
}
