package com.ovoenergy.effect.natchez

import java.io.{InputStream, Reader}
import java.net.URL
import java.sql.{Array => _, _}
import java.util.Calendar

import com.github.ghik.silencer.silent

/**
* This is an absolutely abominable brute force solution to linking PreparedStatements
 * with a SQL string so we can include it in traces but hey I figure it is a one time cost
 * Pretend this doesn't exist and you never had to see it
 */
@silent("deprecated")
private[natchez] case class TracedStatement(
  underlying: PreparedStatement,
  queryString: String
) extends PreparedStatement {
  def executeQuery(): ResultSet = underlying.executeQuery()
  def executeUpdate(): Int = underlying.executeUpdate()
  def setNull(parameterIndex: Int, sqlType: Int): Unit = underlying.setNull(parameterIndex, sqlType)
  def setBoolean(parameterIndex: Int, x: Boolean): Unit = underlying.setBoolean(parameterIndex, x)
  def setByte(parameterIndex: Int, x: Byte): Unit = underlying.setByte(parameterIndex, x)
  def setShort(parameterIndex: Int, x: Short): Unit = underlying.setShort(parameterIndex, x)
  def setInt(parameterIndex: Int, x: Int): Unit = underlying.setInt(parameterIndex, x)
  def setLong(parameterIndex: Int, x: Long): Unit = underlying.setLong(parameterIndex, x)
  def setFloat(parameterIndex: Int, x: Float): Unit = underlying.setFloat(parameterIndex, x)
  def setDouble(parameterIndex: Int, x: Double): Unit = underlying.setDouble(parameterIndex, x)
  def setBigDecimal(parameterIndex: Int, x: java.math.BigDecimal): Unit = underlying.setBigDecimal(parameterIndex, x)
  def setString(parameterIndex: Int, x: String): Unit = underlying.setString(parameterIndex, x)
  def setBytes(parameterIndex: Int, x: Array[Byte]): Unit = underlying.setBytes(parameterIndex, x)
  def setDate(parameterIndex: Int, x: Date): Unit = underlying.setDate(parameterIndex, x)
  def setTime(parameterIndex: Int, x: Time): Unit = underlying.setTime(parameterIndex, x)
  def setTimestamp(parameterIndex: Int, x: Timestamp): Unit = underlying.setTimestamp(parameterIndex, x)
  def setAsciiStream(parameterIndex: Int, x: InputStream, length: Int): Unit = underlying.setAsciiStream(parameterIndex, x, length)
  def setUnicodeStream(parameterIndex: Int, x: InputStream, length: Int): Unit = underlying.setUnicodeStream(parameterIndex, x, length)
  def setBinaryStream(parameterIndex: Int, x: InputStream, length: Int): Unit = underlying.setBinaryStream(parameterIndex, x, length)
  def clearParameters(): Unit = underlying.clearParameters()
  def setObject(parameterIndex: Int, x: Any, targetSqlType: Int): Unit = underlying.setObject(parameterIndex, x, targetSqlType)
  def setObject(parameterIndex: Int, x: Any): Unit = underlying.setObject(parameterIndex, x)
  def execute(): Boolean = underlying.execute()
  def addBatch(): Unit = underlying.addBatch()
  def setCharacterStream(parameterIndex: Int, reader: Reader, length: Int): Unit = underlying.setCharacterStream(parameterIndex, reader, length)
  def setRef(parameterIndex: Int, x: Ref): Unit = underlying.setRef(parameterIndex, x)
  def setBlob(parameterIndex: Int, x: Blob): Unit = underlying.setBlob(parameterIndex, x)
  def setClob(parameterIndex: Int, x: Clob): Unit = underlying.setClob(parameterIndex, x)
  def setArray(parameterIndex: Int, x: java.sql.Array): Unit = underlying.setArray(parameterIndex, x)
  def getMetaData: ResultSetMetaData = underlying.getMetaData
  def setDate(parameterIndex: Int, x: Date, cal: Calendar): Unit = underlying.setDate(parameterIndex, x, cal)
  def setTime(parameterIndex: Int, x: Time, cal: Calendar): Unit = underlying.setTime(parameterIndex, x, cal)
  def setTimestamp(parameterIndex: Int, x: Timestamp, cal: Calendar): Unit = underlying.setTimestamp(parameterIndex, x, cal)
  def setNull(parameterIndex: Int, sqlType: Int, typeName: String): Unit = underlying.setNull(parameterIndex, sqlType, typeName)
  def setURL(parameterIndex: Int, x: URL): Unit = underlying.setURL(parameterIndex, x)
  def getParameterMetaData: ParameterMetaData = underlying.getParameterMetaData
  def setRowId(parameterIndex: Int, x: RowId): Unit = underlying.setRowId(parameterIndex, x)
  def setNString(parameterIndex: Int, value: String): Unit = underlying.setNString(parameterIndex, value)
  def setNCharacterStream(parameterIndex: Int, value: Reader, length: Long): Unit = underlying.setNCharacterStream(parameterIndex, value, length)
  def setNClob(parameterIndex: Int, value: NClob): Unit = underlying.setNClob(parameterIndex, value)
  def setClob(parameterIndex: Int, reader: Reader, length: Long): Unit = underlying.setClob(parameterIndex, reader)
  def setBlob(parameterIndex: Int, inputStream: InputStream, length: Long): Unit = underlying.setBlob(parameterIndex, inputStream, length)
  def setNClob(parameterIndex: Int, reader: Reader, length: Long): Unit = underlying.setNClob(parameterIndex, reader, length)
  def setSQLXML(parameterIndex: Int, xmlObject: SQLXML): Unit = underlying.setSQLXML(parameterIndex, xmlObject)
  def setObject(parameterIndex: Int, x: Any, targetSqlType: Int, scaleOrLength: Int): Unit = underlying.setObject(parameterIndex, targetSqlType, scaleOrLength)
  def setAsciiStream(parameterIndex: Int, x: InputStream, length: Long): Unit = underlying.setAsciiStream(parameterIndex, x, length)
  def setBinaryStream(parameterIndex: Int, x: InputStream, length: Long): Unit = underlying.setBinaryStream(parameterIndex, x, length)
  def setCharacterStream(parameterIndex: Int, reader: Reader, length: Long): Unit = underlying.setCharacterStream(parameterIndex, reader, length)
  def setAsciiStream(parameterIndex: Int, x: InputStream): Unit = underlying.setAsciiStream(parameterIndex, x)
  def setBinaryStream(parameterIndex: Int, x: InputStream): Unit = underlying.setBinaryStream(parameterIndex, x)
  def setCharacterStream(parameterIndex: Int, reader: Reader): Unit = underlying.setCharacterStream(parameterIndex, reader)
  def setNCharacterStream(parameterIndex: Int, value: Reader): Unit = underlying.setNCharacterStream(parameterIndex, value)
  def setClob(parameterIndex: Int, reader: Reader): Unit = underlying.setClob(parameterIndex, reader)
  def setBlob(parameterIndex: Int, inputStream: InputStream): Unit = underlying.setBlob(parameterIndex, inputStream)
  def setNClob(parameterIndex: Int, reader: Reader): Unit = underlying.setNClob(parameterIndex, reader)
  def executeQuery(sql: String): ResultSet = underlying.executeQuery(sql)
  def executeUpdate(sql: String): Int = underlying.executeUpdate(sql)
  def close(): Unit = underlying.close()
  def getMaxFieldSize: Int = underlying.getMaxFieldSize
  def setMaxFieldSize(max: Int): Unit = underlying.setMaxFieldSize(max)
  def getMaxRows: Int = underlying.getMaxRows
  def setMaxRows(max: Int): Unit = underlying.setMaxRows(max)
  def setEscapeProcessing(enable: Boolean): Unit = underlying.setEscapeProcessing(enable)
  def getQueryTimeout: Int = underlying.getQueryTimeout
  def setQueryTimeout(seconds: Int): Unit = underlying.setQueryTimeout(seconds)
  def cancel(): Unit = underlying.cancel()
  def getWarnings: SQLWarning = underlying.getWarnings
  def clearWarnings(): Unit = underlying.clearWarnings()
  def setCursorName(name: String): Unit = underlying.setCursorName(name)
  def execute(sql: String): Boolean = underlying.execute(sql)
  def getResultSet: ResultSet = underlying.getResultSet
  def getUpdateCount: Int = underlying.getUpdateCount
  def getMoreResults: Boolean = underlying.getMoreResults()
  def setFetchDirection(direction: Int): Unit = underlying.setFetchDirection(direction)
  def getFetchDirection: Int = underlying.getFetchDirection
  def setFetchSize(rows: Int): Unit = underlying.setFetchSize(rows)
  def getFetchSize: Int = underlying.getFetchSize
  def getResultSetConcurrency: Int = underlying.getResultSetConcurrency
  def getResultSetType: Int = underlying.getResultSetType
  def addBatch(sql: String): Unit = underlying.addBatch(sql)
  def clearBatch(): Unit = underlying.clearBatch()
  def executeBatch(): Array[Int] = underlying.executeBatch()
  def getConnection: Connection = underlying.getConnection
  def getMoreResults(current: Int): Boolean = underlying.getMoreResults()
  def getGeneratedKeys: ResultSet = underlying.getGeneratedKeys
  def executeUpdate(sql: String, autoGeneratedKeys: Int): Int = underlying.executeUpdate(sql, autoGeneratedKeys)
  def executeUpdate(sql: String, columnIndexes: Array[Int]): Int = underlying.executeUpdate(sql, columnIndexes)
  def executeUpdate(sql: String, columnNames: Array[String]): Int = underlying.executeUpdate(sql, columnNames)
  def execute(sql: String, autoGeneratedKeys: Int): Boolean = underlying.execute(sql, autoGeneratedKeys)
  def execute(sql: String, columnIndexes: Array[Int]): Boolean = underlying.execute(sql, columnIndexes)
  def execute(sql: String, columnNames: Array[String]): Boolean = underlying.execute(sql, columnNames)
  def getResultSetHoldability: Int = underlying.getResultSetHoldability
  def isClosed: Boolean = underlying.isClosed
  def setPoolable(poolable: Boolean): Unit = underlying.setPoolable(poolable)
  def isPoolable: Boolean = underlying.isPoolable
  def closeOnCompletion(): Unit = underlying.closeOnCompletion()
  def isCloseOnCompletion: Boolean = underlying.isCloseOnCompletion
  def unwrap[T](iface: Class[T]): T = underlying.unwrap(iface)
  def isWrapperFor(iface: Class[_]): Boolean = underlying.isWrapperFor(iface)
}

