drop table preio 

----- enumeraciones ------
create type COMBUSTIBLE as enum(
	'Diesel',
	'GNC',
	'Nafta'
);

create type ESTADO as enum(
	'Disponible',
	'En Uso',
	'En Mantenimiento'
);

create type TIPOVEHICULO as enum(
	'Maquina',
	'Camioneta'
);

create type SERVICIOS as enum(
	'Combustible',
	'Repuestos',
	'Otro'
);

create table Vehiculo(
	id_vehiculo INT primary key,
	patente VARCHAR(15),
	capacidad_tanque INT,
	tipo_combustible COMBUSTIBLE,
	estado ESTADO,
	fecha_mantenimiento DATE,
	kilometraje INT,
	tipo_vehiculo	TIPOVEHICULO,
	consumo_promedio DECIMAL(3,2)
);

create table Proveedor(
	id_proveedor INT primary key,
	nombre VARCHAR(50),
	razon_social VARCHAR(50),
	servicio SERVICIOS
);

create table Precio(
	id_precio INT primary key,
	precio_unitario INT,
	fecha_desde TIMESTAMP,
	fecha_hasta TIMESTAMP
);

create table Direccion(
	id_direccion INT primary key,
	calle VARCHAR(100),
	numero VARCHAR(5),
	codigo_postal VARCHAR(4),
	barrio VARCHAR(100),
	departamento CHAR
);

------ tablas con relaciones simples
create table Persona(
	id_persona INT primary key,
	nombre VARCHAR(50),
	apellido VARCHAR(50),
	documento VARCHAR(8),
	telefono VARCHAR(20),
	id_direccion INT,
	
	constraint fk_persona_direccion
		foreign key (id_direccion)
		references Direccion (id_direccion)
);


create table Empleado(
	id_empleado INT primary key,
	fecha_alta DATE,
	fecha_baja DATE,
	id_persona INT,
	
	constraint fk_empleado_persona
		foreign key (id_persona)
		references Persona (id_persona)
);

--------- tablas con relaciones multiples ---------

create table Ticket(
	id_ticket INT primary key,
	importe_total DECIMAL(10,2),
	litros DECIMAL(3,2),
	id_precio INT,
	id_proveedor INT,
	id_responsable_carga INT,
	
	constraint fk_ticket_precio
		foreign key (id_precio)
		references Precio (id_precio),
		
	constraint fk_ticket_proveedor
		foreign key (id_proveedor)
		references Proveedor (id_proveedor),
	
	constraint fk_ticket_empleado
		foreign key (id_responsable_carga)
		references Empleado (id_empleado)
);


------- tablas intermedias ------

create table VehiculoEmpleado(
	id_empleado INT,
	id_vehiculo INT,
	
	constraint pk_vehiculo_empleado
		primary key (id_empleado, id_vehiculo),
	
	constraint fk_vehiculoempleado_empleado
		foreign key (id_empleado)
		references Empleado (id_empleado),
	
	constraint fk_vehiculoempleado_vehiculo
		foreign key (id_vehiculo)
		references Vehiculo (id_vehiculo)
)



