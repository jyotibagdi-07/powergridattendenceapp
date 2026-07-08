package com.example.powergridattendance

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object EmployeeRepository {

    private val employees = mutableListOf<Employee>()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        loadEmployees(context)
        isInitialized = true
    }

    private fun loadEmployees(context: Context) {
        try {
            val file = File(context.filesDir, "employees.json")
            if (file.exists()) {
                val jsonString = file.readText()
                val jsonArray = JSONArray(jsonString)
                employees.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    employees.add(
                        Employee(
                            employeeId = obj.getString("employeeId"),
                            employeeName = obj.getString("employeeName"),
                            imagePath = obj.getString("imagePath")
                        )
                    )
                }
                Log.d("EmployeeRepository", "Loaded ${employees.size} employees from disk")
            }
        } catch (e: Exception) {
            Log.e("EmployeeRepository", "Error loading employees", e)
        }
    }

    private fun saveEmployees(context: Context) {
        try {
            val jsonArray = JSONArray()
            for (employee in employees) {
                val obj = JSONObject().apply {
                    put("employeeId", employee.employeeId)
                    put("employeeName", employee.employeeName)
                    put("imagePath", employee.imagePath)
                }
                jsonArray.put(obj)
            }
            val file = File(context.filesDir, "employees.json")
            file.writeText(jsonArray.toString())
            Log.d("EmployeeRepository", "Saved ${employees.size} employees to disk")
        } catch (e: Exception) {
            Log.e("EmployeeRepository", "Error saving employees", e)
        }
    }

    fun addEmployee(context: Context, employee: Employee) {
        employees.add(employee)
        saveEmployees(context)
    }

    fun getEmployeeById(employeeId: String): Employee? {
        return employees.find { it.employeeId == employeeId }
    }

    fun getAllEmployees(): List<Employee> {
        return employees
    }

    fun clearAllEmployees(context: Context) {
        try {
            val file = File(context.filesDir, "employees.json")
            if (file.exists()) {
                file.delete()
            }
            context.filesDir.listFiles()?.forEach { f ->
                if (f.name.endsWith("_face.jpg")) {
                    f.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("EmployeeRepository", "Error clearing employees", e)
        }
        employees.clear()
        RecognitionHelper.clearCache()
    }
}