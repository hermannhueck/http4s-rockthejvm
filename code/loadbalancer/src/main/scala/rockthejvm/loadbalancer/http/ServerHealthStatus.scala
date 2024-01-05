package rockthejvm.loadbalancer.http

sealed trait ServerHealthStatus {
  def widen: ServerHealthStatus = this
}
object ServerHealthStatus       {
  case object Alive extends ServerHealthStatus
  case object Dead  extends ServerHealthStatus
}
