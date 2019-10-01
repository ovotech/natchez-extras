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
  p: PreparedStatement,
  queryString: String
) extends PreparedStatement {
  def executeQuery(): ResultSet = p.executeQuery()
  def executeUpdate(): Int = p.executeUpdate()
  def setNull(parameterIndex: Int, sqlType: Int): Unit = p.setNull(parameterIndex, sqlType)
  def setBoolean(parameterIndex: Int, x: Boolean): Unit = p.setBoolean(parameterIndex, x)
  def setByte(parameterIndex: Int, x: Byte): Unit = p.setByte(parameterIndex, x)
  def setShort(parameterIndex: Int, x: Short): Unit = p.setShort(parameterIndex, x)
  def setInt(parameterIndex: Int, x: Int): Unit = p.setInt(parameterIndex, x)
  def setLong(parameterIndex: Int, x: Long): Unit = p.setLong(parameterIndex, x)
  def setFloat(parameterIndex: Int, x: Float): Unit = p.setFloat(parameterIndex, x)
  def setDouble(parameterIndex: Int, x: Double): Unit = p.setDouble(parameterIndex, x)
  def setBigDecimal(parameterIndex: Int, x: java.math.BigDecimal): Unit = p.setBigDecimal(parameterIndex, x)
  def setString(parameterIndex: Int, x: String): Unit = p.setString(parameterIndex, x)
  def setBytes(parameterIndex: Int, x: Array[Byte]): Unit = p.setBytes(parameterIndex, x)
  def setDate(parameterIndex: Int, x: Date): Unit = p.setDate(parameterIndex, x)
  def setTime(parameterIndex: Int, x: Time): Unit = p.setTime(parameterIndex, x)
  def setTimestamp(parameterIndex: Int, x: Timestamp): Unit = p.setTimestamp(parameterIndex, x)
  def setAsciiStream(parameterIndex: Int, x: InputStream, length: Int): Unit = p.setAsciiStream(parameterIndex, x, length)
  def setUnicodeStream(parameterIndex: Int, x: InputStream, length: Int): Unit = p.setUnicodeStream(parameterIndex, x, length)
  def setBinaryStream(parameterIndex: Int, x: InputStream, length: Int): Unit = p.setBinaryStream(parameterIndex, x, length)
  def clearParameters(): Unit = p.clearParameters()
  def setObject(parameterIndex: Int, x: Any, targetSqlType: Int): Unit = p.setObject(parameterIndex, x, targetSqlType)
  def setObject(parameterIndex: Int, x: Any): Unit = p.setObject(parameterIndex, x)
  def execute(): Boolean = p.execute()
  def addBatch(): Unit = p.addBatch()
  def setCharacterStream(parameterIndex: Int, reader: Reader, length: Int): Unit = p.setCharacterStream(parameterIndex, reader, length)
  def setRef(parameterIndex: Int, x: Ref): Unit = p.setRef(parameterIndex, x)
  def setBlob(parameterIndex: Int, x: Blob): Unit = p.setBlob(parameterIndex, x)
  def setClob(parameterIndex: Int, x: Clob): Unit = p.setClob(parameterIndex, x)
  def setArray(parameterIndex: Int, x: java.sql.Array): Unit = p.setArray(parameterIndex, x)
  def getMetaData: ResultSetMetaData = p.getMetaData
  def setDate(parameterIndex: Int, x: Date, cal: Calendar): Unit = p.setDate(parameterIndex, x, cal)
  def setTime(parameterIndex: Int, x: Time, cal: Calendar): Unit = p.setTime(parameterIndex, x, cal)
  def setTimestamp(parameterIndex: Int, x: Timestamp, cal: Calendar): Unit = p.setTimestamp(parameterIndex, x, cal)
  def setNull(parameterIndex: Int, sqlType: Int, typeName: String): Unit = p.setNull(parameterIndex, sqlType, typeName)
  def setURL(parameterIndex: Int, x: URL): Unit = p.setURL(parameterIndex, x)
  def getParameterMetaData: ParameterMetaData = p.getParameterMetaData
  def setRowId(parameterIndex: Int, x: RowId): Unit = p.setRowId(parameterIndex, x)
  def setNString(parameterIndex: Int, value: String): Unit = p.setNString(parameterIndex, value)
  def setNCharacterStream(parameterIndex: Int, value: Reader, length: Long): Unit = p.setNCharacterStream(parameterIndex, value, length)
  def setNClob(parameterIndex: Int, value: NClob): Unit = p.setNClob(parameterIndex, value)
  def setClob(parameterIndex: Int, reader: Reader, length: Long): Unit = p.setClob(parameterIndex, reader)
  def setBlob(parameterIndex: Int, inputStream: InputStream, length: Long): Unit = p.setBlob(parameterIndex, inputStream, length)
  def setNClob(parameterIndex: Int, reader: Reader, length: Long): Unit = p.setNClob(parameterIndex, reader, length)
  def setSQLXML(parameterIndex: Int, xmlObject: SQLXML): Unit = p.setSQLXML(parameterIndex, xmlObject)
  def setObject(parameterIndex: Int, x: Any, targetSqlType: Int, scaleOrLength: Int): Unit = p.setObject(parameterIndex, targetSqlType, scaleOrLength)
  def setAsciiStream(parameterIndex: Int, x: InputStream, length: Long): Unit = p.setAsciiStream(parameterIndex, x, length)
  def setBinaryStream(parameterIndex: Int, x: InputStream, length: Long): Unit = p.setBinaryStream(parameterIndex, x, length)
  def setCharacterStream(parameterIndex: Int, reader: Reader, length: Long): Unit = p.setCharacterStream(parameterIndex, reader, length)
  def setAsciiStream(parameterIndex: Int, x: InputStream): Unit = p.setAsciiStream(parameterIndex, x)
  def setBinaryStream(parameterIndex: Int, x: InputStream): Unit = p.setBinaryStream(parameterIndex, x)
  def setCharacterStream(parameterIndex: Int, reader: Reader): Unit = p.setCharacterStream(parameterIndex, reader)
  def setNCharacterStream(parameterIndex: Int, value: Reader): Unit = p.setNCharacterStream(parameterIndex, value)
  def setClob(parameterIndex: Int, reader: Reader): Unit = p.setClob(parameterIndex, reader)
  def setBlob(parameterIndex: Int, inputStream: InputStream): Unit = p.setBlob(parameterIndex, inputStream)
  def setNClob(parameterIndex: Int, reader: Reader): Unit = p.setNClob(parameterIndex, reader)
  def executeQuery(sql: String): ResultSet = p.executeQuery(sql)
  def executeUpdate(sql: String): Int = p.executeUpdate(sql)
  def close(): Unit = p.close()
  def getMaxFieldSize: Int = p.getMaxFieldSize
  def setMaxFieldSize(max: Int): Unit = p.setMaxFieldSize(max)
  def getMaxRows: Int = p.getMaxRows
  def setMaxRows(max: Int): Unit = p.setMaxRows(max)
  def setEscapeProcessing(enable: Boolean): Unit = p.setEscapeProcessing(enable)
  def getQueryTimeout: Int = p.getQueryTimeout
  def setQueryTimeout(seconds: Int): Unit = p.setQueryTimeout(seconds)
  def cancel(): Unit = p.cancel()
  def getWarnings: SQLWarning = p.getWarnings
  def clearWarnings(): Unit = p.clearWarnings()
  def setCursorName(name: String): Unit = p.setCursorName(name)
  def execute(sql: String): Boolean = p.execute(sql)
  def getResultSet: ResultSet = p.getResultSet
  def getUpdateCount: Int = p.getUpdateCount
  def getMoreResults: Boolean = p.getMoreResults()
  def setFetchDirection(direction: Int): Unit = p.setFetchDirection(direction)
  def getFetchDirection: Int = p.getFetchDirection
  def setFetchSize(rows: Int): Unit = p.setFetchSize(rows)
  def getFetchSize: Int = p.getFetchSize
  def getResultSetConcurrency: Int = p.getResultSetConcurrency
  def getResultSetType: Int = p.getResultSetType
  def addBatch(sql: String): Unit = p.addBatch(sql)
  def clearBatch(): Unit = p.clearBatch()
  def executeBatch(): Array[Int] = p.executeBatch()
  def getConnection: Connection = p.getConnection
  def getMoreResults(current: Int): Boolean = p.getMoreResults()
  def getGeneratedKeys: ResultSet = p.getGeneratedKeys
  def executeUpdate(sql: String, autoGeneratedKeys: Int): Int = p.executeUpdate(sql, autoGeneratedKeys)
  def executeUpdate(sql: String, columnIndexes: Array[Int]): Int = p.executeUpdate(sql, columnIndexes)
  def executeUpdate(sql: String, columnNames: Array[String]): Int = p.executeUpdate(sql, columnNames)
  def execute(sql: String, autoGeneratedKeys: Int): Boolean = p.execute(sql, autoGeneratedKeys)
  def execute(sql: String, columnIndexes: Array[Int]): Boolean = p.execute(sql, columnIndexes)
  def execute(sql: String, columnNames: Array[String]): Boolean = p.execute(sql, columnNames)
  def getResultSetHoldability: Int = p.getResultSetHoldability
  def isClosed: Boolean = p.isClosed
  def setPoolable(poolable: Boolean): Unit = p.setPoolable(poolable)
  def isPoolable: Boolean = p.isPoolable
  def closeOnCompletion(): Unit = p.closeOnCompletion()
  def isCloseOnCompletion: Boolean = p.isCloseOnCompletion
  def unwrap[T](iface: Class[T]): T = p.unwrap(iface)
  def isWrapperFor(iface: Class[_]): Boolean = p.isWrapperFor(iface)
}

