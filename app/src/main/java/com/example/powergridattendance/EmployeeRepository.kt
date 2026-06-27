package com.example.powergridattendance

object EmployeeRepository {

    val employees =
        mutableListOf<Employee>()

    fun addEmployee(
        employee: Employee
    ) {
        employees.add(employee)
    }

    fun getEmployeeById(
        employeeId: String
    ): Employee? {

        return employees.find {
            it.employeeId == employeeId
        }
    }

    fun getAllEmployees():
            List<Employee> {

        return employees
    }
}